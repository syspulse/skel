package io.syspulse.skel.db.guard

import scala.util.{Failure,Success,Try}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

/**
 * QueryGuard trait - validates queries using a sequence of rules.
 * All rules must pass for the query to be allowed.
 */
trait QueryGuard {
  def isAllowed(q: String, opts: Map[String, Any] = Map()): Try[Boolean]
}

/**
 * QueryGuard companion object containing Rule types and implementations.
 */
object QueryGuard {
  /**
   * Rule trait for firewall-style query validation.
   * Each rule validates a specific aspect of the query.
   */
  trait Rule {
    def name: String
    def validate(query: String, queryUpper: String, opts: Map[String, Any]): Try[Boolean]
  }

  /**
   * RuleKeyword - blocks queries containing specific keywords.
   * Useful for blocking DDL statements like CREATE, DROP, etc.
   */
  class RuleKeyword(
    blockedKeywords: Set[String],
    checkAtStart: Boolean = true,
    checkInBody: Boolean = true
  ) extends Rule {
    private val log = Logger(this.getClass)
    
    def name: String = "RuleKeyword"
    
    def validate(query: String, queryUpper: String, opts: Map[String, Any]): Try[Boolean] = {
      val hasBlockedKeyword = blockedKeywords.exists { keyword =>
        val keywordUpper = keyword.toUpperCase
        (checkAtStart && queryUpper.startsWith(keywordUpper)) ||
        (checkInBody && queryUpper.contains(s" ${keywordUpper} "))
      }
      
      if (hasBlockedKeyword) {
        log.warn(s"Blocked keyword detected in query: ${query}")
        Failure(new Exception(s"DDL statements are not allowed (blocked keywords: ${blockedKeywords.mkString(", ")})"))
      } else {
        Success(true)
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
    def forDangerousFunctions(): RuleKeyword = new RuleKeyword(DEFAULT_DANGEROUS_FUNCTIONS, checkAtStart = false, checkInBody = true)
  }

  /**
   * RuleTenant - ensures tenant isolation for SELECT queries.
   * Requires that queries include tenantId when a tenantId is provided in opts.
   */
  class RuleTenant(
    tenantFieldNames: Set[String] = Set("tenantId", "TENANTID", "TENANT_ID", "tenant_id")
  ) extends Rule {
    private val log = Logger(this.getClass)
    
    def name: String = "RuleTenant"
    
    def validate(query: String, queryUpper: String, opts: Map[String, Any]): Try[Boolean] = {
      val tenantId = opts.get("tenantId")
      
      // Only check tenant isolation for SELECT queries when tenantId is provided
      if (queryUpper.contains("SELECT") && tenantId.isDefined) {
        val hasTenantField = tenantFieldNames.exists(fieldName => 
          queryUpper.contains(fieldName.toUpperCase)
        )
        
        if (!hasTenantField) {
          log.warn(s"Query missing tenant isolation: ${query}")
          Failure(new Exception("Query must include tenant isolation (tenantId field required)"))
        } else {
          Success(true)
        }
      } else {
        // No tenant check needed (not a SELECT or no tenantId provided)
        Success(true)
      }
    }
  }

  object RuleTenant {
    def default(): RuleTenant = new RuleTenant()
    def apply(tenantFieldNames: Set[String]): RuleTenant = new RuleTenant(tenantFieldNames)
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
    
    def validate(query: String, queryUpper: String, opts: Map[String, Any]): Try[Boolean] = {
      val queryToCheck = if (caseSensitive) query else queryUpper
      
      val hasSuspiciousPattern = blockedPatterns.exists { pattern =>
        val patternToCheck = if (caseSensitive) pattern else pattern.toUpperCase
        queryToCheck.contains(patternToCheck)
      }
      
      if (hasSuspiciousPattern) {
        log.warn(s"Blocked suspicious pattern in query: ${query}")
        Failure(new Exception(s"Suspicious query patterns detected (blocked patterns: ${blockedPatterns.mkString(", ")})"))
      } else {
        Success(true)
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
class RuleBasedGuard(rules: Seq[QueryGuard.Rule]) extends QueryGuard {
  private val log = Logger(this.getClass)
  
  def isAllowed(q: String, opts: Map[String, Any] = Map()): Try[Boolean] = {
    try {
      val queryUpper = q.toUpperCase.trim
      
      // Apply all rules sequentially - all must pass
      rules.foldLeft[Try[Boolean]](Success(true)) { (acc, rule) =>
        acc match {
          case Success(true) =>
            // Previous rules passed, check this rule
            rule.validate(q, queryUpper, opts) match {
              case Success(true) => Success(true) // Rule passed
              case Success(false) =>
                log.warn(s"Rule '${rule.name}' blocked query: ${q}")
                Failure(new Exception(s"Query blocked by rule '${rule.name}'"))
              case f @ Failure(_) => f // Rule validation failed
            }
          case Success(false) =>
            // Previous rule blocked, short-circuit
            acc
          case f @ Failure(_) => f // Previous rule failed, short-circuit
        }
      }
    } catch {
      case e: Exception =>
        log.error(s"Error validating query: ${q}", e)
        Failure(e)
    }
  }
}

/**
 * PassGuard - allows all queries (no validation)
 */
class PassGuard extends QueryGuard {
  def isAllowed(q: String, opts: Map[String, Any] = Map()): Try[Boolean] = {
    Success(true)
  }
}

object PassGuard extends PassGuard

/**
 * ElasticGuard - maintains backward compatibility with the original ElasticGuard behavior.
 * Uses the new rule-based system internally.
 */
class ElasticGuard() extends QueryGuard {
  private val log = Logger(this.getClass)
  
  // Create rule-based guard with all the original rules
  private val guard = new RuleBasedGuard(Seq(
    QueryGuard.RuleKeyword.forDDL(),
    QueryGuard.RuleKeyword.forDangerousFunctions(),
    QueryGuard.RuleTenant.default(),
    QueryGuard.RulePattern.default()
  ))
  
  def isAllowed(q: String, opts: Map[String, Any] = Map()): Try[Boolean] = {
    guard.isAllowed(q, opts)
  }
}
