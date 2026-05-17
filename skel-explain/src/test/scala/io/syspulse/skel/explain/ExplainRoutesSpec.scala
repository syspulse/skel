package io.syspulse.skel.explain

import org.scalatest.{BeforeAndAfterAll}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import spray.json._

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import io.syspulse.skel.explain.server._
import io.syspulse.skel.explain.store._
import io.syspulse.skel.explain.server.ExplainJson._
import scala.concurrent.Promise
import io.syspulse.skel.auth.jwt.AuthJwt
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection

class ExplainRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  // disable GraalVM polyglot interpreter warning
  sys.props("polyglot.engine.WarnInterpreterOnly") = "false"

  implicit val config: Config = Config(
    ownerAttr = "oid",
    rolesAttr = "groups[].",
    serviceRole = "explain-service",
    adminRole = "explain-admin",
    permissions = "user"
  )

  val store = new ExplainStoreMem()
  val typedSystem = akka.actor.typed.ActorSystem(Behaviors.empty, "ExplainTestSystem")
  val registry = typedSystem.systemActorOf(ExplainRegistry(store), "ExplainRegistry")

  val routesPromise = Promise[ExplainRoutes]()
  val testBehavior = Behaviors.setup[Any] { context =>
    val routes = new ExplainRoutes(registry)(context, config)
    routesPromise.success(routes)
    Behaviors.empty
  }
  val testActor = typedSystem.systemActorOf(testBehavior, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = {
    typedSystem.terminate()
  }

  val jwtSecret = "secret1"
  val jwtAlgo = JwtAlgorithm.HS256
  AuthJwt(s"${jwtAlgo}://${jwtSecret}")

  val adminRole = "explain-admin"
  val serviceRole = "explain-service"
  val userRole = "explain-user"

  def createJwtToken(oid: String, roles: Seq[String] = Seq("explain-user")): String = {
    val claims = JwtClaim(
      issuer = Some("test"),
      subject = Some("test-user"),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      expiration = Some((System.currentTimeMillis() / 1000) + 3600),
      content = s"""{"oid":"$oid","groups":["${roles.mkString("\",\"")}"],"tenantId":"$oid"}"""
    )
    Jwt.encode(claims, jwtSecret, jwtAlgo)
  }

  // Shared test wallet data (as in Test-1.md)
  val walletData = JsObject(
    "address" -> JsString("0x9000000000000000000000000000000000000000"),
    "network" -> JsString("ethereum"),
    "name" -> JsString("Wallet-1"),
    "metadata" -> JsObject(
      "tx_hash" -> JsString("0x770bc9a1f7c32cb63a5002b9ceb5c7994cd3af0fc6b2309cb32d3c46f629daa0"),
      "tx_from" -> JsString("0xA911Ff351B143634Dbc5aF3E204EA074583A83e3"),
      "balance" -> JsNumber(100),
      "threshold" -> JsString("> 1000.0"),
      "wallet" -> JsString("0x9000000000000000000000000000000000000000")
    )
  )

  "ExplainRoutes HTTP endpoints" should {

    // ====================== Rule CRUD tests ======================

    "[admin] create a default rule (no oid)" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      val req = ExplainRuleCreateReq(
        scripts = Seq(ScriptDef("js", "input.toUpperCase()")),
        name = Some("DefaultDetectorWallet")
      )

      Post("/rule/DetectorWallet", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRuleRes]
          r.oid shouldBe ""
          r.rid shouldBe "DetectorWallet"
        }
    }

    "[admin] get the default rule" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      Get("/rule/DetectorWallet") ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRule]
          r.oid shouldBe ""
          r.rid shouldBe "DetectorWallet"
          r.scripts shouldBe Seq(ScriptDef("js", "input.toUpperCase()"))
        }
    }

    "[user] reject non-admin access to default rule CRUD" in {
      val jwtUser = createJwtToken("490", Seq(userRole))

      val req = ExplainRuleCreateReq(scripts = Seq(ScriptDef("str", "")), name = None)

      val r = Post("/rule/SomeRule", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtUser))) ~>
        routes.routes

      r.rejections should contain(AuthorizationFailedRejection)
    }

    "[admin] create OID-specific rule" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      val req = ExplainRuleCreateReq(
        scripts = Seq(ScriptDef("js", "\"Custom-OID-490: \" + input")),
        name = Some("Custom490Rule")
      )

      Post("/490/DetectorWallet", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRuleRes]
          r.oid shouldBe "490"
          r.rid shouldBe "DetectorWallet"
        }
    }

    "[user 490] create own OID rule" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val req = ExplainRuleCreateReq(
        scripts = Seq(ScriptDef("js", "input.length.toString()")),
        name = Some("User490Rule")
      )

      Post("/490/LengthRule", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRuleRes]
          r.oid shouldBe "490"
          r.rid shouldBe "LengthRule"
        }
    }

    "[user 490] reject creating rule for different oid" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val req = ExplainRuleCreateReq(scripts = Seq(ScriptDef("str", "")), name = None)

      val r = Post("/999/SomeRule", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes

      r.rejections should contain(AuthorizationFailedRejection)
    }

    "[user 490] update own OID rule" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val updateReq = ExplainRuleUpdateReq(
        scripts = Some(Seq(ScriptDef("js", "input.toLowerCase()"))),
        name = Some("Updated490Rule")
      )

      Put("/490/LengthRule", updateReq) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRuleRes]
          r.oid shouldBe "490"
          r.rid shouldBe "LengthRule"
        }

      // Verify the rule was updated
      Get("/490/LengthRule") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRule]
          r.scripts shouldBe Seq(ScriptDef("js", "input.toLowerCase()"))
        }
    }

    "[user 490] delete own OID rule" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      Delete("/490/LengthRule") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRuleRes]
          r.oid shouldBe "490"
          r.rid shouldBe "LengthRule"
        }
    }

    "[unauthenticated] reject rule CRUD without JWT" in {
      val req = ExplainRuleCreateReq(scripts = Seq(ScriptDef("str", "")), name = None)

      val r = Post("/rule/SomeRule", req) ~>
        routes.routes

      r.rejections should not be empty
    }

    // ====================== Explain endpoint tests ======================

    "[explain] use default rule when no oid provided" in {
      // Default rule "DetectorWallet" was created above: input.toUpperCase()
      val req = ExplainReq(
        oid = None,
        data = JsObject("address" -> JsString("0xabc"))
      )

      Post("/DetectorWallet", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should not be empty
          r.oid shouldBe Some("")
        }
    }

    "[explain] custom oid rule overrides default rule" in {
      // OID 490 has js://"Custom-OID-490: " + input
      val req = ExplainReq(
        oid = Some("490"),
        data = JsObject("address" -> JsString("0xabc"))
      )

      Post("/DetectorWallet", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should include("Custom-OID-490")
          r.oid shouldBe Some("490")
        }
    }

    "[explain] fallback to default rule when oid rule not found" in {
      // oid=999 has no DetectorWallet rule - falls back to default oid=""
      val req = ExplainReq(
        oid = Some("999"),
        data = JsObject("text" -> JsString("fallback"))
      )

      Post("/DetectorWallet", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should not be empty
          r.oid shouldBe Some("")  // returns the actual rule's oid (default)
        }
    }

    "[explain] return error when no rule found at all" in {
      val req = ExplainReq(
        oid = None,
        data = JsObject("text" -> JsString("no rule"))
      )

      Post("/NonExistentRule", req) ~>
        routes.routes ~>
        check {
          status should not be StatusCodes.OK
        }
    }

    "[explain] ScriptJS processes wallet data" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      val jsScript = ScriptDef("js",
        "var d=JSON.parse(input); var m=d.metadata; 'Sender ['+m.tx_from+'] triggered balance change. Balance: '+m.balance+' threshold: '+m.threshold"
      )

      Post("/rule/WalletExplain", ExplainRuleCreateReq(scripts = Seq(jsScript), name = Some("WalletJS"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(oid = None, data = walletData)

      Post("/WalletExplain", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should include("0xA911Ff351B143634Dbc5aF3E204EA074583A83e3")
          r.explanation should include("100")
          r.scripts should not be empty
        }
    }

    "[explain] different rules produce different results" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      Post("/rule/Rule-A", ExplainRuleCreateReq(scripts = Seq(ScriptDef("js", "\"RULE-A: \" + input")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      Post("/rule/Rule-B", ExplainRuleCreateReq(scripts = Seq(ScriptDef("js", "\"RULE-B: \" + input")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      var resultA = ""
      var resultB = ""

      Post("/Rule-A", ExplainReq(oid = None, data = JsObject("v" -> JsString("test")))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          resultA = responseAs[ExplainRes].explanation
        }

      Post("/Rule-B", ExplainReq(oid = None, data = JsObject("v" -> JsString("test")))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          resultB = responseAs[ExplainRes].explanation
        }

      resultA should include("RULE-A")
      resultB should include("RULE-B")
      resultA should not equal resultB
    }

    "[explain] ScriptFlow with multiple scripts (ScriptJS chain)" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      val scripts = Seq(
        ScriptDef("js", "JSON.parse(input).metadata.balance.toString()"),
        ScriptDef("js", "\"Balance is: \" + input")
      )

      Post("/rule/ChainRule", ExplainRuleCreateReq(scripts = scripts, name = Some("ChainedFlow"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(oid = None, data = walletData)

      Post("/ChainRule", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "Balance is: 100"
          r.scripts.size shouldBe 2
        }
    }

    "[explain] OID 490 custom rule with ScriptJS for wallet explanation (like Test-1.md)" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      val jsExplainScript = ScriptDef("js",
        "var d=JSON.parse(input); var m=d.metadata; " +
        "'Sender ['+m.tx_from+'](https://etherscan.io/address/'+m.tx_from.toLowerCase()+') " +
        "triggered balance change on ['+m.wallet+']'"
      )

      Post("/490/WalletFullExplain", ExplainRuleCreateReq(scripts = Seq(jsExplainScript), name = Some("WalletFull"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(oid = Some("490"), data = walletData)

      Post("/WalletFullExplain", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should include("0xA911Ff351B143634Dbc5aF3E204EA074583A83e3")
          r.explanation should include("etherscan.io")
          r.oid shouldBe Some("490")
        }
    }

    "[explain] ScriptAI in ScriptFlow chain (uses ScriptJS as substitute)" in {
      val jwtAdmin = createJwtToken("999", Seq(adminRole))

      val scripts = Seq(
        ScriptDef("js", "JSON.parse(input).name"),
        ScriptDef("js", "\"The wallet name is: \" + input")
      )

      Post("/rule/AiSimulate", ExplainRuleCreateReq(scripts = scripts, name = Some("AiSimulate"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(oid = None, data = walletData)

      Post("/AiSimulate", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "The wallet name is: Wallet-1"
          r.scripts.size shouldBe 2
          r.scripts should contain("js")
        }
    }

    "[service] service account can manage any oid rule" in {
      val jwtService = createJwtToken("svc-account", Seq(serviceRole))

      val req = ExplainRuleCreateReq(scripts = Seq(ScriptDef("js", "\"svc-rule: \" + input")), name = None)

      Post("/490/ServiceRule", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtService))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRuleRes]
          r.oid shouldBe "490"
          r.rid shouldBe "ServiceRule"
        }
    }
  }
}
