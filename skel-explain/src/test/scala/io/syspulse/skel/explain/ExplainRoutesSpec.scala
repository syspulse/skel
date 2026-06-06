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

    "[admin oid=''] create a default rule via POST /{rid}" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      val req = ExplainCreateReq(
        scripts = Seq(ExplainScript("js", "input.toUpperCase()")),
        name = Some("DefaultDetectorWallet")
      )

      Post("/DetectorWallet", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe None
          r.rid shouldBe "DetectorWallet"
        }
    }

    "[admin oid=''] get the default rule via GET /{rid}" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      Get("/DetectorWallet") ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explain]
          r.oid shouldBe None
          r.rid shouldBe "DetectorWallet"
          r.scripts shouldBe Seq(ExplainScript("js", "input.toUpperCase()"))
        }
    }

    "[admin] create a rule for a specific oid via POST /{rid}?oid=" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      val jwt530 = createJwtToken("530", Seq(userRole))

      val req = ExplainCreateReq(
        scripts = Seq(ExplainScript("js", "\"admin-created oid 530\"")),
        name = Some("AdminOid530Rule")
      )

      Post("/AdminOidCreate?oid=530", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("530")
          r.rid shouldBe "AdminOidCreate"
        }

      Get("/AdminOidCreate") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt530))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explain]
          r.oid shouldBe Some("530")
          r.rid shouldBe "AdminOidCreate"
          r.scripts shouldBe Seq(ExplainScript("js", "\"admin-created oid 530\""))
        }
    }

    "[user 490] create own rule via POST /{rid}" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val req = ExplainCreateReq(
        scripts = Seq(ExplainScript("js", "\"Custom-OID-490: \" + input")),
        name = Some("Custom490Rule")
      )

      Post("/DetectorWallet", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("490")
          r.rid shouldBe "DetectorWallet"
        }
    }

    "[user 490] reject creating a rule for a different oid via POST /{rid}?oid=" in {
      val jwt490 = createJwtToken("490", Seq(userRole))
      val req = ExplainCreateReq(scripts = Seq(ExplainScript("str", "")), name = None)

      val r = Post("/OtherOidRule?oid=530", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes

      r.rejections should contain(AuthorizationFailedRejection)
    }

    "[admin] create rule from body rid and URL oid overrides body oid" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      val jwt530 = createJwtToken("530", Seq(userRole))
      val req = ExplainCreateReq(
        oid = Some("490"),
        rid = Some("BodyRidCreate"),
        scripts = Seq(ExplainScript("js", "\"created from body rid\"")),
        name = Some("BodyRidCreateRule")
      )

      Post("/?oid=530", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("530")
          r.rid shouldBe "BodyRidCreate"
        }

      Get("/BodyRidCreate") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt530))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explain]
          r.oid shouldBe Some("530")
          r.rid shouldBe "BodyRidCreate"
        }
    }

    "[user 490] create second own rule" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val req = ExplainCreateReq(
        scripts = Seq(ExplainScript("js", "input.length.toString()")),
        name = Some("User490LengthRule")
      )

      Post("/LengthRule", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("490")
          r.rid shouldBe "LengthRule"
        }
    }

    "[user 490] update own rule via PUT /{rid}" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val updateReq = ExplainUpdateReq(
        scripts = Some(Seq(ExplainScript("js", "input.toLowerCase()"))),
        name = Some("Updated490Rule")
      )

      Put("/LengthRule", updateReq) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("490")
          r.rid shouldBe "LengthRule"
        }

      Get("/LengthRule") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explain]
          r.scripts shouldBe Seq(ExplainScript("js", "input.toLowerCase()"))
        }
    }

    "[user 490] delete own rule via DELETE /{rid}" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      Delete("/LengthRule") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("490")
          r.rid shouldBe "LengthRule"
        }
    }

    "[unauthenticated] reject rule CRUD without JWT" in {
      val req = ExplainCreateReq(scripts = Seq(ExplainScript("str", "")), name = None)

      val r = Post("/SomeRule", req) ~> routes.routes

      r.rejections should not be empty
    }

    // ====================== Explain endpoint tests ======================

    "[explain] GET /{rid}/explain uses default rule when no oid in body" in {
      // Default rule "DetectorWallet" (oid="") created above: input.toUpperCase()
      Get("/DetectorWallet/explain") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should not be empty
          r.rid shouldBe "DetectorWallet"
          r.oid shouldBe None
          r.fmt shouldBe Some("markdown")
        }
    }

    "[explain] GET /{rid}/explain with oid=490 in body uses custom rule" in {
      val req = ExplainReq(oid = Some("490"))

      Get("/DetectorWallet/explain", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should include("Custom-OID-490")
          r.rid shouldBe "DetectorWallet"
          r.oid shouldBe Some("490")
        }
    }

    "[explain] GET /{rid}/explain falls back to default when oid rule not found" in {
      val req = ExplainReq(oid = Some("999"))

      Get("/DetectorWallet/explain", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should not be empty
          r.oid shouldBe None
        }
    }

    "[explain] GET /{rid}/explain returns error when no rule found" in {
      Get("/NonExistentRule/explain") ~>
        routes.routes ~>
        check {
          status should not be StatusCodes.OK
        }
    }

    "[explain] style param is passed to scripts via dataMap" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      Post("/StyleRule", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"fixed\"")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      Get("/StyleRule/explain?style=short") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "fixed"
        }

      Get("/StyleRule/explain?style=narrative") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "fixed"
        }
    }

    "[explain] ScriptJS processes wallet data passed in body" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      val jsScript = ExplainScript("js",
        "var d=JSON.parse(input); var m=d.metadata; 'Sender ['+m.tx_from+'] triggered balance change. Balance: '+m.balance+' threshold: '+m.threshold"
      )

      Post("/WalletExplain", ExplainCreateReq(scripts = Seq(jsScript), name = Some("WalletJS"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(data = walletData)

      Get("/WalletExplain/explain", req) ~>
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
      val jwtDef = createJwtToken("", Seq(adminRole))

      Post("/Rule-A", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"RULE-A: \" + input")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      Post("/Rule-B", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"RULE-B: \" + input")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      var resultA = ""
      var resultB = ""

      Get("/Rule-A/explain") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          resultA = responseAs[ExplainRes].explanation
        }

      Get("/Rule-B/explain") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          resultB = responseAs[ExplainRes].explanation
        }

      resultA should include("RULE-A")
      resultB should include("RULE-B")
      resultA should not equal resultB
    }

    "[explain] ScriptFlow with multiple scripts (chain)" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      val scripts = Seq(
        ExplainScript("js", "JSON.parse(input).metadata.balance.toString()"),
        ExplainScript("js", "\"Balance is: \" + input")
      )

      Post("/ChainRule", ExplainCreateReq(scripts = scripts, name = Some("ChainedFlow"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(data = walletData)

      Get("/ChainRule/explain", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "Balance is: 100"
          r.scripts.size shouldBe 2
        }
    }

    "[explain] oid=490 custom rule with ScriptJS for wallet explanation" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val jsExplainScript = ExplainScript("js",
        "var d=JSON.parse(input); var m=d.metadata; " +
        "'Sender ['+m.tx_from+'](https://etherscan.io/address/'+m.tx_from.toLowerCase()+') " +
        "triggered balance change on ['+m.wallet+']'"
      )

      Post("/WalletFullExplain", ExplainCreateReq(scripts = Seq(jsExplainScript), name = Some("WalletFull"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(oid = Some("490"), data = walletData)

      Get("/WalletFullExplain/explain", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation should include("0xA911Ff351B143634Dbc5aF3E204EA074583A83e3")
          r.explanation should include("etherscan.io")
          r.oid shouldBe Some("490")
        }
    }

    "[explain] ScriptFlow chain (two JS steps)" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      val scripts = Seq(
        ExplainScript("js", "JSON.parse(input).name"),
        ExplainScript("js", "\"The wallet name is: \" + input")
      )

      Post("/AiSimulate", ExplainCreateReq(scripts = scripts, name = Some("AiSimulate"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      val req = ExplainReq(data = walletData)

      Get("/AiSimulate/explain", req) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "The wallet name is: Wallet-1"
          r.scripts.size shouldBe 2
          r.scripts should contain("js")
        }
    }

    "[service] service account can create and explain rules" in {
      val jwtService = createJwtToken("svc-account", Seq(serviceRole))

      val req = ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"svc-rule: \" + input")), name = None)

      Post("/ServiceRule", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtService))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("svc-account")
          r.rid shouldBe "ServiceRule"
        }
    }

    "[explain] Rule_1 resolves to oid-specific explanations for oid 490 and 530 only" in {
      val jwt490 = createJwtToken("490", Seq(userRole))
      val jwt530 = createJwtToken("530", Seq(userRole))

      Post("/Rule_1", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"specific explanation for 490\"")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("490")
          r.rid shouldBe "Rule_1"
        }

      Post("/Rule_1", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"specific explanation for 530\"")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt530))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("530")
          r.rid shouldBe "Rule_1"
        }

      Get("/Rule_1") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explain]
          r.oid shouldBe Some("490")
          r.rid shouldBe "Rule_1"
          r.scripts.head.src shouldBe "\"specific explanation for 490\""
        }

      Get("/Rule_1") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt530))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explain]
          r.oid shouldBe Some("530")
          r.rid shouldBe "Rule_1"
          r.scripts.head.src shouldBe "\"specific explanation for 530\""
        }

      Get("/Rule_1/explain") ~>
        routes.routes ~>
        check {
          status should not be StatusCodes.OK
        }

      Get("/Rule_1/explain", ExplainReq(oid = Some("490"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "specific explanation for 490"
          r.oid shouldBe Some("490")
        }

      Get("/Rule_1/explain", ExplainReq(oid = Some("530"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "specific explanation for 530"
          r.oid shouldBe Some("530")
        }
    }

    "[explain] Rule_1 uses default explanation except for oid 490 and 530 overrides" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      val jwt490 = createJwtToken("490", Seq(userRole))
      val jwt530 = createJwtToken("530", Seq(userRole))

      Post("/Rule_1", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"default explanation\"")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe None
          r.rid shouldBe "Rule_1"
        }

      Post("/Rule_1", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"specific explanation for 490\"")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("490")
          r.rid shouldBe "Rule_1"
        }

      Post("/Rule_1", ExplainCreateReq(scripts = Seq(ExplainScript("js", "\"specific explanation for 530\"")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt530))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplaineActionRes]
          r.oid shouldBe Some("530")
          r.rid shouldBe "Rule_1"
        }

      Get("/Rule_1/explain") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "default explanation"
          r.oid shouldBe None
        }

      Get("/Rule_1/explain", ExplainReq(oid = Some("490"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "specific explanation for 490"
          r.explanation should not be "default explanation"
          r.oid shouldBe Some("490")
        }

      Get("/Rule_1/explain", ExplainReq(oid = Some("530"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "specific explanation for 530"
          r.explanation should not be "default explanation"
          r.oid shouldBe Some("530")
        }

      Get("/Rule_1/explain?oid=530", ExplainReq(oid = Some("490"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "specific explanation for 530"
          r.oid shouldBe Some("530")
        }

      Get("/Rule_1/explain", ExplainReq(oid = Some("490"), rid = Some("NotRule_1"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "specific explanation for 490"
          r.oid shouldBe Some("490")
        }

      Get("/", ExplainReq(oid = Some("530"), rid = Some("Rule_1"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "specific explanation for 530"
          r.oid shouldBe Some("530")
        }

      Get("/Rule_1/explain", ExplainReq(oid = Some("999"))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe "default explanation"
          r.oid shouldBe None
        }
    }

    // ====================== Bulk delete (DELETE /?oid=) ======================

    "[bulk-delete] DELETE / with no oid deletes all default-oid rules" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      // seed two default rules
      Post("/BulkDel-A", ExplainCreateReq(scripts = Seq(ExplainScript("str", "a")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~> routes.routes ~> check { status shouldBe StatusCodes.OK }
      Post("/BulkDel-B", ExplainCreateReq(scripts = Seq(ExplainScript("str", "b")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~> routes.routes ~> check { status shouldBe StatusCodes.OK }

      Delete("/") ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explains]
          r.data.map(_.rid).toSet should contain allOf ("BulkDel-A", "BulkDel-B")
          r.total.getOrElse(0L) should be >= 2L
        }

      // rules must be gone
      Get("/BulkDel-A") ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status should not be StatusCodes.OK }
    }

    "[bulk-delete] DELETE /?oid=490 deletes all rules for oid 490" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      Post("/BulkOid-X", ExplainCreateReq(scripts = Seq(ExplainScript("str", "x")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~> routes.routes ~> check { status shouldBe StatusCodes.OK }
      Post("/BulkOid-Y", ExplainCreateReq(scripts = Seq(ExplainScript("str", "y")), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~> routes.routes ~> check { status shouldBe StatusCodes.OK }

      Delete("/?oid=490") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explains]
          r.data.map(_.oid).forall(_ == Some("490")) shouldBe true
          r.data.map(_.rid).toSet should contain allOf ("BulkOid-X", "BulkOid-Y")
        }

      Get("/BulkOid-X") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes ~>
        check { status should not be StatusCodes.OK }
    }

    "[bulk-delete] user cannot DELETE /?oid= of a different oid" in {
      val jwt490 = createJwtToken("490", Seq(userRole))

      val r = Delete("/?oid=999") ~>
        addHeader(Authorization(OAuth2BearerToken(jwt490))) ~>
        routes.routes

      r.rejections should contain(AuthorizationFailedRejection)
    }

    "[bulk-delete] unauthenticated DELETE / is rejected" in {
      val r = Delete("/") ~> routes.routes
      r.rejections should not be empty
    }

    "[search] find rules by prefix, middle and postfix in name (GET /?search=)" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      val marker = "prefixAlphabetapostfix"

      Post("/SearchPrefix", ExplainCreateReq(scripts = Seq(ExplainScript("str", "")), name = Some(marker))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      Get(Uri("/").withQuery(Uri.Query("search" -> "pre"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Explains].data.map(_.rid) should contain("SearchPrefix")
        }

      Get(Uri("/").withQuery(Uri.Query("search" -> "pha"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Explains].data.map(_.rid) should contain("SearchPrefix")
        }

      Get(Uri("/").withQuery(Uri.Query("search" -> "fix"))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Explains].data.map(_.rid) should contain("SearchPrefix")
        }
    }

    "[search] find rules by prefix, middle and postfix in description (POST /search)" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      val marker = "prefixAlphabetapostfix"

      Post("/SearchPostfix", ExplainCreateReq(scripts = Seq(ExplainScript("str", "")), desc = Some(marker))) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      Post("/search", ExplainSearchReq(query = "pre")) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Explains].data.map(_.rid) should contain("SearchPostfix")
        }

      Post("/search", ExplainSearchReq(query = "pha")) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Explains].data.map(_.rid) should contain("SearchPostfix")
        }

      Post("/search", ExplainSearchReq(query = "fix")) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Explains].data.map(_.rid) should contain("SearchPostfix")
        }
    }

    "[bulk-delete] DELETE /?oid= returns empty list when oid has no rules" in {
      val jwtDef = createJwtToken("", Seq(adminRole))

      Delete("/?oid=no-such-oid") ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explains]
          r.data shouldBe empty
          r.total shouldBe Some(0)
        }
    }

    // ====================== Quote handling in src ======================

    "[quotes] src with embedded quotes survives HTTP round-trip" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      // src contains literal double-quote characters: This is "quoted" text
      val src = """This is "quoted" text"""

      val req = ExplainCreateReq(scripts = Seq(ExplainScript("str", src)), name = None)

      Post("/QuoteRoundTrip", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      Get("/QuoteRoundTrip") ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[Explain]
          r.scripts.head.src shouldBe src
        }
    }

    "[quotes] JS src with string literal quotes executes correctly" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      // JS: "Hello \"World\"" evaluates to: Hello "World"
      val src = "\"Hello \\\"World\\\"\""

      val req = ExplainCreateReq(scripts = Seq(ExplainScript("js", src)), name = None)

      Post("/QuoteJsExec", req) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      Get("/QuoteJsExec/explain") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe """Hello "World""""
        }
    }

    "[quotes] JS src building result string with quotes survives round-trip and executes" in {
      val jwtDef = createJwtToken("", Seq(adminRole))
      // src: var d=JSON.parse(input); "Address: \"" + d.address + "\""
      val src = "var d=JSON.parse(input); \"Address: \\\"\" + d.address + \"\\\"\""
      val data = JsObject("address" -> JsString("0xABC"))

      Post("/QuoteJsComplex", ExplainCreateReq(scripts = Seq(ExplainScript("js", src)), name = None)) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check { status shouldBe StatusCodes.OK }

      // Verify src was persisted correctly
      Get("/QuoteJsComplex") ~>
        addHeader(Authorization(OAuth2BearerToken(jwtDef))) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Explain].scripts.head.src shouldBe src
        }

      // Verify execution produces expected output
      Get("/QuoteJsComplex/explain", ExplainReq(data = data)) ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r = responseAs[ExplainRes]
          r.explanation shouldBe """Address: "0xABC""""
        }
    }
  }
}
