package io.syspulse.skel.db.guard

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class QueryParseSpec extends AnyWordSpec with Matchers {

  "QueryGuard.parse for SQL" should {

    "parse simple SELECT without attacks" in {
      val q = "SELECT id, name FROM users WHERE id = 1"
      val parsed = QueryGuard.parse(q, Map("lang" -> "sql")).get

      parsed.lang.id shouldBe "sql"
      parsed.verbs should contain only "SELECT"
      parsed.values.exists(v => v.keyNorm == "id" && v.opNorm == "=" && v.valueNorm.contains("1")) shouldBe true
    }

    "detect DDL verbs in multi-statement query" in {
      val q =
        """SELECT * FROM users;
          |DROP TABLE tenants;
          |CREATE TABLE evil(id INT);
          |""".stripMargin

      val parsed = QueryGuard.parse(q, Map("lang" -> "sql")).get

      parsed.verbs should contain("SELECT")
      parsed.verbs should contain("DROP")
      parsed.verbs should contain("CREATE")
    }

    "ignore comments when extracting verbs and predicates" in {
      val q =
        """-- this is a comment with DROP TABLE x
          |SELECT * FROM t -- another comment with tenantId = 999
          |WHERE tenantId = 300 AND user IS NOT NULL
          |/* multi-line
          |   CREATE TABLE foo
          |*/
          |""".stripMargin

      val parsed = QueryGuard.parse(q, Map("lang" -> "sql")).get

      parsed.verbs should contain only "SELECT"
      parsed.values.exists(v =>
        v.keyNorm == "tenantid" && v.opNorm == "=" && v.valueNorm.contains("300")
      ) shouldBe true
      parsed.values.exists(v =>
        v.keyNorm == "user" && v.opNorm == "IS NOT" && v.valueNorm.contains("NULL")
      ) shouldBe true
    }

    "extract tenantId = 300 and reject tenantId = 301 via RuleTenant + opts" in {
      val rule = QueryGuard.RuleTenant.default()

      // allowed: tenantId = 300 when opts specify tenantId 300
      val qAllowed = "SELECT * FROM orders WHERE tenantId = 300"
      val optsAllowed: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsedAllowed = QueryGuard.parse(qAllowed, optsAllowed).get
      val rvAllowed = rule.validate(parsedAllowed, optsAllowed).get

      rvAllowed.isPassed shouldBe true

      // rejected: tenantId = 301 when only tenantId 300 is allowed
      val qRejected = "SELECT * FROM orders WHERE tenantId = 301"
      val optsRejected: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsedRejected = QueryGuard.parse(qRejected, optsRejected).get
      val rvRejected = rule.validate(parsedRejected, optsRejected).get

      rvRejected.isPassed shouldBe false
    }

    "reject when tenantId predicate is missing even if other predicates exist" in {
      val rule = QueryGuard.RuleTenant.default()

      val q = "SELECT * FROM orders WHERE userId = 42 AND status = 'OPEN'"
      val opts: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsed = QueryGuard.parse(q, opts).get
      val rv = rule.validate(parsed, opts).get

      rv.isPassed shouldBe false
    }

    "parse complex WHERE with multiple tenantId predicates" in {
      val q =
        """SELECT * FROM orders
          |WHERE (tenantId = 300 AND status = 'OPEN')
          |   OR (tenantId = 301 AND status = 'PENDING')
          |""".stripMargin

      val parsed = QueryGuard.parse(q, Map("lang" -> "sql")).get

      val values = parsed.values.filter(_.keyNorm == "tenantid")
      values.map(_.valueNorm.getOrElse("")).toSet shouldBe Set("300", "301")
    }

    "respect RuleTenant table filter so non-matching tables are allowed" in {
      // RuleTenant applies only to "projects" table
      val rule = QueryGuard.RuleTenant.forTables(Set("projects"))

      // Query against USERS with forbidden tenantId should be allowed (table not in filter)
      val qUsers = "SELECT tenantId FROM USERS WHERE tenantId = 301"
      val optsUsers: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsedUsers = QueryGuard.parse(qUsers, optsUsers).get
      parsedUsers.tables should contain ("users")
      val rvUsers = rule.validate(parsedUsers, optsUsers).get
      rvUsers.isPassed shouldBe true

      // Query against projects with forbidden tenantId should be rejected
      val qProjects = "SELECT tenantId FROM projects WHERE tenantId = 301"
      val optsProjects: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsedProjects = QueryGuard.parse(qProjects, optsProjects).get
      parsedProjects.tables should contain ("projects")
      val rvProjects = rule.validate(parsedProjects, optsProjects).get
      rvProjects.isPassed shouldBe false
    }

    "enforce RuleTenant on multiple protected tables with single allowed tenant" in {
      // Protect three tables: project, project_user, report; only tenantId 300 is allowed
      val protectedTables = Set("project", "project_user", "report")
      val rule = QueryGuard.RuleTenant.forTables(protectedTables)

      // 1) Access protected table with correct tenantId -> ALLOW
      val qOk = "SELECT * FROM project WHERE tenantId = 300"
      val optsOk: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsedOk = QueryGuard.parse(qOk, optsOk).get
      parsedOk.tables should contain ("project")
      val rvOk = rule.validate(parsedOk, optsOk).get
      rvOk.isPassed shouldBe true

      // 2) Access protected table with different tenantId -> REJECT
      val qBadTenant = "SELECT * FROM project_user WHERE tenantId = 301"
      val optsBadTenant: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsedBadTenant = QueryGuard.parse(qBadTenant, optsBadTenant).get
      parsedBadTenant.tables should contain ("project_user")
      val rvBadTenant = rule.validate(parsedBadTenant, optsBadTenant).get
      rvBadTenant.isPassed shouldBe false

      // 3) Access protected table without tenantId predicate -> REJECT
      val qNoTenant = "SELECT * FROM report WHERE status = 'OPEN'"
      val optsNoTenant: Map[String, Any] = Map("tenantId" -> "300", "lang" -> "sql")
      val parsedNoTenant = QueryGuard.parse(qNoTenant, optsNoTenant).get
      parsedNoTenant.tables should contain ("report")
      val rvNoTenant = rule.validate(parsedNoTenant, optsNoTenant).get
      rvNoTenant.isPassed shouldBe false
    }
  }
}

