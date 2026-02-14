package io.syspulse.skel.db.guard

import scala.util.{Failure,Success,Try}
import com.typesafe.scalalogging.Logger
import com.typesafe.config.{ConfigFactory, ConfigObject, ConfigValue}

import scala.jdk.CollectionConverters._

/**
 * QueryGuard trait - validates queries using a sequence of rules.
 * All rules must pass for the query to be allowed.
 */
trait QueryGuard {
  private val log = Logger(this.getClass)
  
  /**
   * Parse query into structured representation.
   * Language can be selected via opts("lang") = "sql" | "elastic" | "elasticsearch".
   * If absent, the language is auto-detected.
   */
  def parse(q: String, opts: Map[String, Any] = Map()): Try[QueryGuard.QueryParsed] =
    QueryGuard.parse(q, opts)

  /**
   * Validate a query and return structured validation result.
   */
  def validate(q: String, opts: Map[String, Any] = Map()): Try[QueryGuard.RuleResult]

  /**
   * Legacy API: true if validation passed, false/Failure if rejected or failed.
   * Internally delegates to `validate`.
   */
  def isAllowed(q: String, opts: Map[String, Any] = Map()): Try[Boolean] =
    validate(q, opts).map(r => {
      log.debug(s"query='${q}': ${r}")
      r.isPassed
    })
}

/**
 * QueryGuard companion object containing Rule types and implementations.
 */
object QueryGuard {
  
  def resolve(uri: String): QueryGuard = uri.split("://").toList match {
    case "allow" :: _ => QueryGuardAllow    
    case "deny" :: _ => QueryGuardDeny
    case "rules" :: r => 
      val rules = r.map(r => {
        r.split(";").toList match {
          case "ddl" :: Nil => RuleKeyword.forDDL()
          case "dangerous" :: Nil => RuleKeyword.forDangerousFunctions()
          case "pattern" :: p :: Nil => RulePattern(p.split(","))
          case "tenant" :: t :: Nil => RuleTenant(t.split(",").toSet)
          case "tenant" :: t :: table :: Nil => RuleTenant(t.split(",").toSet)
          case _ => throw new Exception(s"Unknown rule: '${r}'")
        }
      })
      new QueryGuardRules(rules)
    case _ => throw new Exception(s"Unknown guard: '${uri}'")
  }

  // -----------------------------
  // Parsed representation
  // -----------------------------

  sealed trait QueryLanguage { def id: String }
  object QueryLanguage {
    case object SQL extends QueryLanguage { val id: String = "sql" }
    case object Elastic extends QueryLanguage { val id: String = "elastic" }

    def fromAny(v: Option[Any]): Option[QueryLanguage] =
      v.map(_.toString.trim.toLowerCase).flatMap {
        case "sql" => Some(SQL)
        case "elastic" | "elasticsearch" | "es" | "dsl" => Some(Elastic)
        case _ => None
      }

    def detect(q: String): QueryLanguage = {
      val s = Option(q).getOrElse("").trim
      if (s.startsWith("{") || s.startsWith("[")) Elastic else SQL
    }
  }

  /**
   * (key, op, value) triple extracted from the query.
   * Example: ("tenantId","=",Some("300")) or ("user","IS NOT",Some("NULL"))
   */
  case class QueryValue(key: String, op: String, value: Option[String]) {
    def keyNorm: String = key.trim.toLowerCase
    def opNorm: String = op.trim.toUpperCase
    def valueNorm: Option[String] = value.map(_.trim)
  }

  /**
   * Parsed query:
   *  - verbs: statement types (SELECT, DROP, CREATE, ...)
   *  - tables: set of referenced base tables (best-effort, SQL only)
   *  - values: extracted predicates (best-effort)
   */
  case class QueryParsed(
    verbs: Set[String],
    tables: Set[String],
    values: Seq[QueryValue],
    lang: QueryLanguage,
    raw: String
  )

  // -----------------------------
  // Parsing entry points
  // -----------------------------

  def parse(q: String, opts: Map[String, Any] = Map()): Try[QueryParsed] = {
    val lang = QueryLanguage.fromAny(opts.get("lang")).getOrElse(QueryLanguage.detect(q))
    parse(q, lang)
  }

  def parse(q: String, lang: QueryLanguage): Try[QueryParsed] =
    lang match {
      case QueryLanguage.SQL => parseSql(q)
      case QueryLanguage.Elastic => parseElasticDsl(q)
    }

  // -----------------------------
  // SQL parsing (lightweight)
  // -----------------------------

  private def stripSqlComments(q: String): String = {
    if (q == null) return ""
    val noBlock = q.replaceAll("(?s)/\\*.*?\\*/", " ")
    noBlock.replaceAll("(?m)--.*?$", " ")
  }

  private def unquoteJsonStringMaybe(q: String): String = {
    val s = Option(q).getOrElse("").trim
    if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
      s.substring(1, s.length - 1)
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
        .trim
    } else s
  }

  private def splitStatements(q: String): Seq[String] = {
    val s = Option(q).getOrElse("")
    val out = scala.collection.mutable.ArrayBuffer.empty[String]
    val buf = new StringBuilder()
    var inSingle = false
    var inDouble = false
    var i = 0
    while (i < s.length) {
      val ch = s.charAt(i)
      ch match {
        case '\'' if !inDouble =>
          inSingle = !inSingle
          buf.append(ch)
        case '"' if !inSingle =>
          inDouble = !inDouble
          buf.append(ch)
        case ';' if !inSingle && !inDouble =>
          val st = buf.toString().trim
          if (st.nonEmpty) out += st
          buf.clear()
        case _ =>
          buf.append(ch)
      }
      i += 1
    }
    val last = buf.toString().trim
    if (last.nonEmpty) out += last
    out.toSeq
  }

  private val SqlClauseStopWords = Seq("GROUP BY", "ORDER BY", "LIMIT", "HAVING", "WINDOW", "FETCH", "OFFSET", "JOIN")

  private def extractSqlTables(statement: String): Set[String] = {
    val up = statement.toUpperCase
    val fromIdx = up.indexOf("FROM ")
    if (fromIdx < 0) return Set.empty

    val afterFrom = statement.substring(fromIdx + "FROM ".length)
    val afterFromUp = afterFrom.toUpperCase

    val stopIdx = SqlClauseStopWords
      .flatMap(w => Option(afterFromUp.indexOf(w)).filter(_ >= 0))
      .minOption
      .getOrElse(afterFrom.length)

    val fromSection = afterFrom.substring(0, stopIdx)

    // Split by comma and join keywords, pick base table identifiers
    val tableTokens = fromSection
      .split(",")
      .toSeq
      .flatMap { part =>
        val token = part.trim.split("\\s+").headOption.getOrElse("").trim
        if (token.nonEmpty) Some(token) else None
      }

    tableTokens.map(_.toLowerCase).toSet
  }

  private def extractSqlValues(statement: String): Seq[QueryValue] = {
    val up = statement.toUpperCase
    val whereIdx = up.indexOf("WHERE ")
    if (whereIdx < 0) return Seq.empty

    val afterWhere = statement.substring(whereIdx + "WHERE ".length)
    val afterWhereUp = afterWhere.toUpperCase

    val stopIdx = SqlClauseStopWords
      .flatMap(w => Option(afterWhereUp.indexOf(w)).filter(_ >= 0))
      .minOption
      .getOrElse(afterWhere.length)

    val where = afterWhere.substring(0, stopIdx)

    val out = scala.collection.mutable.ArrayBuffer.empty[QueryValue]

    // IS [NOT] NULL
    val isNullRe = ("(?i)\\b([a-zA-Z_][\\w\\.]*)\\b\\s+IS\\s+(NOT\\s+)?NULL\\b").r
    isNullRe.findAllMatchIn(where).foreach { m =>
      val key = m.group(1)
      val not0 = Option(m.group(2)).map(_.trim.toUpperCase).getOrElse("")
      val op = if (not0.nonEmpty) "IS NOT" else "IS"
      out += QueryValue(key, op, Some("NULL"))
    }

    // binary operators with scalar values
    val binRe = ("(?i)\\b([a-zA-Z_][\\w\\.]*)\\b\\s*(=|!=|<>|>=|<=|>|<)\\s*(" +
      "'([^']*)'|\"([^\"]*)\"|([0-9]+(?:\\.[0-9]+)?)|NULL" +
      ")").r
    binRe.findAllMatchIn(where).foreach { m =>
      val key = m.group(1)
      val op = m.group(2)
      val v =
        Option(m.group(4))
          .orElse(Option(m.group(5)))
          .orElse(Option(m.group(6)))
          .orElse(Option(m.group(3)).map(_.trim).filter(_.equalsIgnoreCase("NULL")))
      out += QueryValue(key, op, v)
    }

    out.toSeq
  }

  private def parseSql(q0: String): Try[QueryParsed] = Try {
    val q = unquoteJsonStringMaybe(stripSqlComments(q0)).trim
    val statements = splitStatements(q)

    val verbs = statements.flatMap(_.trim.split("\\s+").headOption.map(_.toUpperCase)).toSet
    val tables = statements.flatMap(extractSqlTables).toSet
    val values = statements.flatMap(extractSqlValues)

    QueryParsed(verbs = verbs, tables = tables, values = values, lang = QueryLanguage.SQL, raw = q0)
  }

  // --------------------------------
  // ElasticSearch DSL parsing (JSON)
  // --------------------------------

  private def parseElasticDsl(q0: String): Try[QueryParsed] = Try {
    val s = Option(q0).getOrElse("").trim
    if (s.isEmpty) {
      QueryParsed(Set.empty, Set.empty, Seq.empty, QueryLanguage.Elastic, raw = q0)
    } else {
      val cfg = ConfigFactory.parseString(s).resolve()
      val verbs =
        if (cfg.hasPath("query") || cfg.hasPath("aggs") || cfg.hasPath("aggregations")) Set("SEARCH") else Set("REQUEST")

      val values = extractElasticValues(cfg.root())
      QueryParsed(verbs = verbs, tables = Set.empty, values = values, lang = QueryLanguage.Elastic, raw = q0)
    }
  }

  private def extractElasticValues(root: ConfigObject): Seq[QueryValue] = {
    val out = scala.collection.mutable.ArrayBuffer.empty[QueryValue]

    def extractFieldMap(v: ConfigValue): Seq[(String, String)] = {
      v.valueType() match {
        case com.typesafe.config.ConfigValueType.OBJECT =>
          val cfg = v.asInstanceOf[ConfigObject].toConfig
          cfg.root().asScala.toSeq.flatMap { case (k, cv) =>
            cv.valueType() match {
              case com.typesafe.config.ConfigValueType.LIST =>
                val list = cfg.getAnyRefList(k).asScala.map(_.toString).mkString(",")
                Some(k -> list)
              case _ =>
                Some(k -> cfg.getAnyRef(k).toString)
            }
          }
        case _ => Seq.empty
      }
    }

    def visit(value: ConfigValue): Unit = {
      value.valueType() match {
        case com.typesafe.config.ConfigValueType.OBJECT =>
          val obj = value.asInstanceOf[ConfigObject]
          val m = obj.asScala.toMap

          m.get("term").foreach(v => extractFieldMap(v).foreach { case (k, vv) => out += QueryValue(k, "=", Some(vv)) })
          m.get("terms").foreach(v => extractFieldMap(v).foreach { case (k, vv) => out += QueryValue(k, "IN", Some(vv)) })
          m.get("match").foreach(v => extractFieldMap(v).foreach { case (k, vv) => out += QueryValue(k, "MATCH", Some(vv)) })
          m.get("exists").foreach { v =>
            val cfg = v.asInstanceOf[ConfigObject].toConfig
            if (cfg.hasPath("field")) out += QueryValue(cfg.getString("field"), "EXISTS", None)
          }
          m.get("range").foreach { v =>
            v.valueType() match {
              case com.typesafe.config.ConfigValueType.OBJECT =>
                v.asInstanceOf[ConfigObject].asScala.foreach { case (field, condVal) =>
                  if (condVal.valueType() == com.typesafe.config.ConfigValueType.OBJECT) {
                    val c = condVal.asInstanceOf[ConfigObject].toConfig
                    Seq("gt", "gte", "lt", "lte").foreach { op =>
                      if (c.hasPath(op)) out += QueryValue(field, op.toUpperCase, Some(c.getAnyRef(op).toString))
                    }
                  }
                }
              case _ =>
            }
          }

          obj.values().asScala.foreach(visit)

        case com.typesafe.config.ConfigValueType.LIST =>
          // recurse into list items where possible
          val list = value.unwrapped().asInstanceOf[java.util.List[AnyRef]].asScala
          list.foreach {
            case m: java.util.Map[_, _] =>
              val cfg = ConfigFactory.parseMap(m.asInstanceOf[java.util.Map[String, AnyRef]])
              visit(cfg.root())
            case _ =>
          }
        case _ =>
      }
    }

    visit(root)
    out.toSeq
  }

  /**
   * Rule trait for firewall-style query validation.
   * Each rule validates a specific aspect of the query.
   */
  trait Rule {
    def name: String
    def validate(parsed: QueryParsed, opts: Map[String, Any]): Try[RuleResult]
  }

  object Rule {
    val ALLOW: String = "ALLOW"
    val REJECT: String = "REJECT"
  }

  class RuleAllow extends Rule {
    def name: String = "RuleAllow"
    def validate(parsed: QueryParsed, opts: Map[String, Any]): Try[RuleResult] =
      Success(RuleResult(rule = Some(this), reason = Some(Rule.ALLOW), desc = Some("[*ALLOW*]")))
  }
  
  class RuleReject extends Rule {
    def name: String = "RuleReject"
    def validate(parsed: QueryParsed, opts: Map[String, Any]): Try[RuleResult] =
      Success(RuleResult(rule = Some(this), reason = Some(Rule.REJECT), desc = Some("[*REJECT*]")))
  }


  /**
   * Result of validation.
   *
   * @param rule   Rule that produced this result (if any).
   * @param reason Optional reason string: "REJECT" | "ALLOW".
   *               If None, it is treated as "ALLOW".
   */
  case class RuleResult(rule: Option[Rule], reason: Option[String], desc:Option[String] = None) {
    def isPassed: Boolean = reason.forall(_ == Rule.ALLOW)
  }

  /**
   * RuleKeyword - blocks queries containing specific statement verbs.
   * (Backwards compatible name: previously it was substring checks, now it is based on `QueryParsed.verbs`.)
   */
  class RuleKeyword(
    blockedKeywords: Set[String]
  ) extends Rule {
    private val log = Logger(this.getClass)
    
    def name: String = "RuleKeyword"
    
    def validate(parsed: QueryParsed, opts: Map[String, Any]): Try[RuleResult] = {
      val blocked = parsed.verbs.intersect(blockedKeywords.map(_.toUpperCase))
      if (blocked.nonEmpty) {
        log.warn(s"Blocked keyword detected in query: ${parsed.raw}")
        Success(RuleResult(rule = Some(this), reason = Some(Rule.REJECT)))
      } else {
        Success(RuleResult(rule = Some(this), reason = Some(Rule.ALLOW)))
      }
    }
  }

  object RuleKeyword {
    /**
     * Default DDL keywords that should be blocked
     */
    val DEFAULT_DDL_KEYWORDS = Set(
      "CREATE", "DROP", "DELETE", "INSERT", "UPDATE", "ALTER", "TRUNCATE",
      "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL", "MERGE", "UPSERT"
    )
    
    /**
     * Default dangerous functions that could modify data
     */
    val DEFAULT_DANGEROUS_FUNCTIONS = Set(
      "DELETE", "INSERT", "UPDATE", "UPSERT", "MERGE", "REPLACE"
    )
    
    def forDDL(): RuleKeyword = new RuleKeyword(DEFAULT_DDL_KEYWORDS)
    def forDangerousFunctions(): RuleKeyword = new RuleKeyword(DEFAULT_DANGEROUS_FUNCTIONS)
  }

  /**
   * RuleTenant - ensures tenant isolation for SELECT queries.
   * Requires that queries include tenantId when a tenantId is provided in opts,
   * optionally only for a specific set of tables.
   */
  class RuleTenant(
    tenantId0: Set[String] = Set(),
    tables0: Set[String] = Set(),
    tenantFieldNames: Set[String] = Set("tenantId", "TENANTID", "TENANT_ID", "tenant_id"),
    blockedTenantIds: Set[String] = Set()
  ) extends Rule {
    private val log = Logger(this.getClass)
    
    def name: String = "RuleTenant"
    
    def validate(parsed: QueryParsed, opts: Map[String, Any]): Try[RuleResult] = {
      val tenantId = tenantId0 ++ {opts.get("tenantId") match {
        case Some(tenantId) => Set(tenantId.toString)
        case None => Set.empty
      }}

      // Effective table filter: constructor tables + any provided in opts("tables")
      val tablesFromOpts: Set[String] = opts.get("tables") match {
        case Some(s: String) =>
          s.split(",").map(_.trim).filter(_.nonEmpty).map(_.toLowerCase).toSet
        case Some(ss: Seq[_]) =>
          ss.map(_.toString.trim.toLowerCase).filter(_.nonEmpty).toSet
        case Some(set: Set[_]) =>
          set.map(_.toString.trim.toLowerCase).filter(_.nonEmpty)
        case _ => Set.empty[String]
      }
      val tableFilter = (tables0.map(_.toLowerCase) ++ tablesFromOpts).filter(_.nonEmpty)

      // If table filter is non-empty and query doesn't touch these tables, skip tenant checks
      if (tableFilter.nonEmpty) {
        val queryTables = parsed.tables.map(_.toLowerCase)
        val intersects = queryTables.exists(tableFilter.contains)
        if (!intersects) {
          return Success(RuleResult(rule = Some(this), reason = Some(Rule.ALLOW)))
        }
      }

      val tenantFieldNorm = tenantFieldNames.map(_.toLowerCase)
      val tenantPredicates = parsed.values.filter(v => tenantFieldNorm.contains(v.keyNorm))

      // Case: reject specific tenantId (e.g. tenantId = 300 is forbidden)
      val blocked = blockedTenantIds.map(_.toString)
      val hasBlockedTenant = tenantPredicates.exists(v => v.opNorm == "=" && v.valueNorm.exists(blocked.contains))
      if (hasBlockedTenant) {
        log.warn(s"Blocked tenant isolation detected: ${parsed.raw}")
        return Success(RuleResult(rule = Some(this), reason = Some(Rule.REJECT)))
      }

      // Case: enforce tenantId from opts/config
      if (tenantId.nonEmpty) {
        val required = tenantId.map(_.toString)
        val hasRequiredTenant = tenantPredicates.exists(v => v.opNorm == "=" && v.valueNorm.exists(required.contains))
        if (!hasRequiredTenant) {
          log.warn(s"Query missing required tenant isolation: ${parsed.raw}")
          Success(RuleResult(rule = Some(this), reason = Some(Rule.REJECT)))
        } else Success(RuleResult(rule = Some(this), reason = Some(Rule.ALLOW)))
      } else Success(RuleResult(rule = Some(this), reason = Some(Rule.ALLOW)))
    }
  }

  object RuleTenant {
    def default(): RuleTenant = new RuleTenant()
    def apply(tenantId: Set[String]): RuleTenant = new RuleTenant(tenantId0 = tenantId)
    def block(tenantId: Set[String]): RuleTenant = new RuleTenant(tenantId0 = Set.empty, blockedTenantIds = tenantId)
    def forTables(tables: Set[String], tenantId: Set[String] = Set.empty): RuleTenant =
      new RuleTenant(tenantId0 = tenantId, tables0 = tables)
  }

  /**
   * RulePattern - blocks queries containing suspicious patterns.
   * Useful for blocking SQL injection attempts, system schema access, etc.
   */
  class RulePattern(
    blockedPatterns: Seq[String],
    caseSensitive: Boolean = false
  ) extends Rule {
    private val log = Logger(this.getClass)
    
    def name: String = "RulePattern"
    
    def validate(parsed: QueryParsed, opts: Map[String, Any]): Try[RuleResult] = {
      val queryToCheck = if (caseSensitive) parsed.raw else parsed.raw.toUpperCase
      
      val hasSuspiciousPattern = blockedPatterns.exists { pattern =>
        val patternToCheck = if (caseSensitive) pattern else pattern.toUpperCase
        queryToCheck.contains(patternToCheck)
      }
      
      if (hasSuspiciousPattern) {
        log.warn(s"Blocked suspicious pattern in query: ${parsed.raw}")
        Success(RuleResult(rule = Some(this), reason = Some(Rule.REJECT)))
      } else {
        Success(RuleResult(rule = Some(this), reason = Some(Rule.ALLOW)))
      }
    }
  }

  object RulePattern {
    /**
     * Default suspicious patterns that should be blocked
     */
    val DEFAULT_SUSPICIOUS_PATTERNS = Seq(
      "UNION", "INFORMATION_SCHEMA", "SYS.", "PG_", "MYSQL.",
      "SCRIPT", "EVAL", "EXEC", "JAVASCRIPT", "PAINLESS"
    )
    
    def default(): RulePattern = new RulePattern(DEFAULT_SUSPICIOUS_PATTERNS)
    def apply(blockedPatterns: Seq[String], caseSensitive: Boolean = false): RulePattern = 
      new RulePattern(blockedPatterns, caseSensitive)
  }
}

/**
 * Rule-based QueryGuard implementation.
 * Acts like a firewall: applies all rules sequentially, and all must pass.
 */
class QueryGuardRules(rules: Seq[QueryGuard.Rule]) extends QueryGuard {
  private val log = Logger(this.getClass)
  
  def validate(q: String, opts: Map[String, Any] = Map()): Try[QueryGuard.RuleResult] =
    try {
      QueryGuard.parse(q, opts) match {
        case Failure(e) =>
          Failure(e)
        case Success(parsed) =>
          // Apply rules sequentially, return first REJECT; otherwise ALLOW
          var last: QueryGuard.RuleResult = QueryGuard.RuleResult(None, None)
          val it = rules.iterator
          var done = false
          while (it.hasNext && !done) {
            val rule = it.next()
            rule.validate(parsed, opts) match {
              case Failure(e) =>
                return Failure(e)
              case Success(rv) if !rv.isPassed =>
                // first rejection wins
                last = rv
                done = true
              case Success(rv) =>
                last = rv
            }
          }
          Success(last)
      }
    } catch {
      case e: Exception =>
        log.error(s"Error validating query: ${q}", e)
        Failure(e)
    }
}

/**
 * PassGuard - allows all queries (no validation)
 */
class QueryGuardAllow extends QueryGuardRules(Seq(new QueryGuard.RuleAllow())) {
  // def validate(q: String, opts: Map[String, Any] = Map()): Try[QueryGuard.RuleResult] =
  //   Success(QueryGuard.RuleResult(None, Some(QueryGuard.Rule.ALLOW),Some("[ALLOW]")))
}

object QueryGuardAllow extends QueryGuardAllow

/**
 * DenyGuard - denies all queries (no validation)
 */
class QueryGuardDeny extends QueryGuardRules(Seq(new QueryGuard.RuleReject())) {
  // def validate(q: String, opts: Map[String, Any] = Map()): Try[QueryGuard.RuleResult] =
  //   Success(QueryGuard.RuleResult(None, Some(QueryGuard.Rule.REJECT),Some("[DENY]")))
}

object QueryGuardDeny extends QueryGuardDeny

/**
 * ElasticGuard - maintains backward compatibility with the original ElasticGuard behavior.
 * Uses the new rule-based system internally.
 */
class ElasticGuard() extends QueryGuardRules(Seq(
    QueryGuard.RuleKeyword.forDDL(),
    QueryGuard.RuleKeyword.forDangerousFunctions(),
    QueryGuard.RuleTenant.default(),
    QueryGuard.RulePattern.default()
  )) {  
    
  // def validate(q: String, opts: Map[String, Any] = Map()): Try[QueryGuard.RuleResult] =
  //   guard.validate(q, opts)
}
