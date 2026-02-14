package io.syspulse.skel.db.guard

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success, Try}

class QueryGuardSpec extends AnyWordSpec with Matchers {

  "QueryGuard.RuleKeyword" should {
    "allow a simple SELECT" in {
      val rule = QueryGuard.RuleKeyword.forDDL()
      val q = "select 1"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      rule.validate(p, Map.empty) shouldBe Success(QueryGuard.RuleResult(Some(rule), Some(QueryGuard.Rule.ALLOW)))
    }

    "block DDL/DML keywords at start (e.g. DROP)" in {
      val rule = QueryGuard.RuleKeyword.forDDL()
      val q = "DROP TABLE t"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      val r = rule.validate(p, Map.empty)
      r.get.isPassed shouldBe false
    }

    "block blocked keywords in body when surrounded by spaces (e.g. SELECT ... DROP ...)" in {
      val rule = QueryGuard.RuleKeyword.forDDL()
      val q = "SELECT * FROM t ; DROP TABLE x"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      val r = rule.validate(p, Map.empty)
      r.get.isPassed shouldBe false
    }

    "block UPDATE via dangerous-functions rule (verb-based)" in {
      val rule = QueryGuard.RuleKeyword.forDangerousFunctions()

      val q = "UPDATE t SET a=1"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      rule.validate(p, Map.empty).get.isPassed shouldBe false
    }
  }

  "QueryGuard.RuleTenant" should {
    "allow SELECT when no tenantId is provided in opts" in {
      val rule = QueryGuard.RuleTenant.default()
      val q = "SELECT * FROM orders WHERE id = 1"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      rule.validate(p, Map.empty) shouldBe Success(QueryGuard.RuleResult(Some(rule), Some(QueryGuard.Rule.ALLOW)))
    }

    "block SELECT when tenantId is provided but query has no tenant field" in {
      val rule = QueryGuard.RuleTenant.default()
      val q = "SELECT * FROM orders WHERE id = 1"
      val opts = Map[String, Any]("tenantId" -> "t1", "lang" -> "sql")
      val p = QueryGuard.parse(q, opts).get
      val r = rule.validate(p, opts)
      r.get.isPassed shouldBe false
    }

    "allow SELECT when tenantId is provided and query contains matching tenant predicate" in {
      val rule = QueryGuard.RuleTenant.default()
      val q = "SELECT * FROM orders WHERE tenant_id = 't1' AND id = 1"
      val opts = Map[String, Any]("tenantId" -> "t1", "lang" -> "sql")
      val p = QueryGuard.parse(q, opts).get
      val r = rule.validate(p, opts)
      r shouldBe Success(QueryGuard.RuleResult(Some(rule), Some(QueryGuard.Rule.ALLOW)))
    }

    "reject when tenantId is explicitly blocked (tenantId = 300)" in {
      val rule = QueryGuard.RuleTenant.block(Set("300"))
      val q = "SELECT * FROM orders WHERE tenantId = 300"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      rule.validate(p, Map.empty).get.isPassed shouldBe false
    }
  }

  "QueryGuard.RulePattern" should {
    "block suspicious patterns (case-insensitive), e.g. UNION" in {
      val rule = QueryGuard.RulePattern.default()
      val q = "select * from a UnIoN select * from b"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      val r = rule.validate(p, Map.empty)
      r.get.isPassed shouldBe false
    }

    "block system schema access patterns, e.g. PG_" in {
      val rule = QueryGuard.RulePattern.default()
      val q = "select * from pg_catalog.pg_tables"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      val r = rule.validate(p, Map.empty)
      r.get.isPassed shouldBe false
    }
  }

  "QueryGuard.parse" should {
    "extract verbs and values from SQL (tenantId = 300)" in {
      val q = "SELECT * FROM t WHERE tenantId = 300 AND user IS NOT NULL"
      val p = QueryGuard.parse(q, Map("lang" -> "sql")).get
      p.verbs should contain("SELECT")
      p.values.exists(v => v.keyNorm == "tenantid" && v.opNorm == "=" && v.valueNorm.contains("300")) shouldBe true
      p.values.exists(v => v.keyNorm == "user" && v.opNorm == "IS NOT" && v.valueNorm.contains("NULL")) shouldBe true
    }

    "extract values from Elastic DSL term queries (tenantId = 300)" in {
      val q =
        """{
          |  "query": {
          |    "term": { "tenantId": 300 }
          |  }
          |}""".stripMargin
      val p = QueryGuard.parse(q, Map("lang" -> "elastic")).get
      p.verbs should contain("SEARCH")
      p.values.exists(v => v.keyNorm == "tenantid" && v.opNorm == "=" && v.valueNorm.contains("300")) shouldBe true
    }
  }

  "RuleBasedGuard" should {
    "short-circuit after first failing rule" in {
      @volatile var secondRuleCalled = false

      val first = new QueryGuard.Rule {
        override def name: String = "first"
        override def validate(parsed: QueryGuard.QueryParsed, opts: Map[String, Any]): Try[QueryGuard.RuleResult] =
          Success(QueryGuard.RuleResult(rule = Some(this), reason = Some("REJECT")))
      }

      val second = new QueryGuard.Rule {
        override def name: String = "second"
        override def validate(parsed: QueryGuard.QueryParsed, opts: Map[String, Any]): Try[QueryGuard.RuleResult] = {
          secondRuleCalled = true
          Success(QueryGuard.RuleResult(rule = Some(this), reason = None))
        }
      }

      val guard = new QueryGuardRules(Seq(first, second))
      val rv = guard.validate("SELECT 1").get
      rv.isPassed shouldBe false
      rv.rule.map(_.name) shouldBe Some("first")
      secondRuleCalled shouldBe false
    }

    "return Success(true) when all rules pass" in {
      val guard = new QueryGuardRules(Seq(
        QueryGuard.RuleKeyword.forDDL(),
        QueryGuard.RulePattern.default()
      ))
      val rv = guard.validate("SELECT 1").get
      rv.isPassed shouldBe true
    }
  }

  "PassGuard" should {
    "allow everything" in {
      val rv = QueryGuardAllow.validate("DROP TABLE x").get
      rv.isPassed shouldBe true
    }
  }

  "ElasticGuard" should {
    "allow a basic SELECT without tenantId" in {
      val guard = new ElasticGuard()
      guard.isAllowed("SELECT 1") shouldBe Success(true)
    }

    "block DDL/DML like DROP" in {
      val guard = new ElasticGuard()
      guard.isAllowed("DROP TABLE x") shouldBe Success(false)
    }

    "block SELECT without tenant field when tenantId is provided" in {
      val guard = new ElasticGuard()
      guard.isAllowed("SELECT * FROM orders", Map("tenantId" -> "t1")) shouldBe Success(false)
    }

    "block UNION pattern" in {
      val guard = new ElasticGuard()
      guard.isAllowed("SELECT * FROM a UNION SELECT * FROM b") shouldBe Success(false)
    }
  }
}

