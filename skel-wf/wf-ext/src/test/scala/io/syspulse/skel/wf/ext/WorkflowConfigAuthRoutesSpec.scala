package io.syspulse.skel.wf.ext

import org.scalatest.{BeforeAndAfterAll}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import io.syspulse.skel.auth.jwt.AuthJwt
import io.hacken.ext.wf._
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._

/**
 * HTTP auth tests for WorkflowConfig / DetectorConfig optional `oid`/`pid` + JWT rules:
 *   - user: `oid` required and must equal JWT owner (JWT overrides oid for Store/create)
 *   - admin/service: any oid (or omit = no owner filter)
 * JWTs are created with pdi.jwt (same library as auth-core AuthJwt) and verified via AuthJwt.
 */
class WorkflowConfigAuthRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  // Must NOT set GOD - oid filtering relies on ExtRbacUser + JWT claims.
  sys.props.remove("GOD")
  sys.props.remove("god")

  implicit val config: Config = Config(
    ownerAttr = "oid",
    rolesAttr = "groups[].",
    serviceRole = "extractor-service",
    adminRole = "extractor-admin",
    permissions = "user"
  )

  val store = new WorkflowStoreMem()
  val engine = new StubEngine
  val typedSystem = ActorSystem(Behaviors.empty, "WfAuthTestSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  typedSystem.systemActorOf(Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry, engine)(context, config))
    Behaviors.empty
  }, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  private val (rejectionHandler, exceptionHandler) = (new io.syspulse.skel.Server {}).getHandlers()
  val apiRoutes = handleRejections(rejectionHandler) {
    handleExceptions(exceptionHandler) { routes.routes }
  }

  override def afterAll(): Unit = {
    typedSystem.terminate()
    Await.result(typedSystem.whenTerminated, 10.seconds)
    super.afterAll()
  }

  // Same JWT stack as auth-core: pdi.jwt + AuthJwt verifier init
  val jwtSecret = "secret1"
  val jwtAlgo = JwtAlgorithm.HS256
  AuthJwt(s"${jwtAlgo}://${jwtSecret}")

  val adminRole = "extractor-admin"
  val userRole = "extractor-user"

  /** Build a real HS256 JWT (pdi.jwt), matching auth-core claim shape used by ExtAuth/ExtRbacUser. */
  def createJwtToken(oid: String, roles: Seq[String] = Seq(userRole)): String = {
    val claims = JwtClaim(
      issuer = Some("test"),
      subject = Some("test-user"),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      expiration = Some((System.currentTimeMillis() / 1000) + 3600),
      content = s"""{"oid":"$oid","groups":["${roles.mkString("\",\"")}"],"tenantId":"$oid"}"""
    )
    Jwt.encode(claims, jwtSecret, jwtAlgo)
  }

  def withAuth(jwt: String)(req: akka.http.scaladsl.model.HttpRequest) =
    req ~> addHeader(Authorization(OAuth2BearerToken(jwt)))

  def withOid(path: String, oid: String): String =
    if (path.contains("?")) s"$path&oid=$oid" else s"$path?oid=$oid"

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.detector.DetectorSchemaJson._
  import io.hacken.ext.detector.DetectorConfigJson._

  "WorkflowConfig oid/pid HTTP auth" should {

    "bootstrap a WorkflowSchema for config creates" in {
      val jwtAdmin = createJwtToken("", Seq(adminRole))
      withAuth(jwtAdmin)(Post("/schema/dsl", WorkflowSchemaDslReq("Detector.a -> Detector.b", name = Some("AuthSchema")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[WorkflowSchema].name shouldBe "AuthSchema"
        }
    }

    "[admin JWT] create configs for any oid; list all when oid omitted" in {
      val jwtAdmin = createJwtToken("", Seq(adminRole))

      withAuth(jwtAdmin)(Post("/config?oid=490", WorkflowConfigCreateReq(sid = 0, name = Some("c-490-p1"), oid = Some("490"), pid = Some("p1")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          val c = responseAs[WorkflowConfig]
          c.oid shouldBe Some("490")
          c.pid shouldBe Some("p1")
        }

      withAuth(jwtAdmin)(Post("/config?oid=530", WorkflowConfigCreateReq(sid = 0, name = Some("c-530-p1"), oid = Some("530"), pid = Some("p1")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[WorkflowConfig].oid shouldBe Some("530")
        }

      // admin may omit oid -> Store oid=None (no owner filter)
      withAuth(jwtAdmin)(Get("/config")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].total should be >= 2L
      }

      withAuth(jwtAdmin)(Get("/config?oid=490")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        val page = responseAs[WorkflowConfigs]
        page.configs.foreach(_.oid shouldBe Some("490"))
      }
    }

    "[user JWT] access own oid with ?oid= matching JWT" in {
      val jwt490 = createJwtToken("490")

      withAuth(jwt490)(Get(withOid("/config", "490"))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].configs.foreach(_.oid shouldBe Some("490"))
      }

      withAuth(jwt490)(Get(withOid("/config/0", "490"))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigView].config.oid shouldBe Some("490")
      }

      withAuth(jwt490)(Get("/config?oid=490&pid=p1")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].configs.foreach(_.pid shouldBe Some("p1"))
      }
    }

    "[user JWT] reject missing oid / empty oid / mismatched oid" in {
      val jwt490 = createJwtToken("490")

      // missing oid
      val r1 = withAuth(jwt490)(Get("/config")) ~> routes.routes
      r1.rejections should contain(AuthorizationFailedRejection)

      // empty oid
      val r2 = withAuth(jwt490)(Get("/config?oid=")) ~> routes.routes
      r2.rejections should contain(AuthorizationFailedRejection)

      // mismatched oid (JWT=490, param=530)
      val r3 = withAuth(jwt490)(Get("/config?oid=530")) ~> routes.routes
      r3.rejections should contain(AuthorizationFailedRejection)

      val r4 = withAuth(jwt490)(Get("/config/1?oid=530")) ~> routes.routes
      r4.rejections should contain(AuthorizationFailedRejection)

      val r5 = withAuth(jwt490)(Put("/config/0?oid=530", WorkflowConfigUpdateReq(title = Some("hack")))) ~> routes.routes
      r5.rejections should contain(AuthorizationFailedRejection)

      val r6 = withAuth(jwt490)(Delete("/config/0?oid=530")) ~> routes.routes
      r6.rejections should contain(AuthorizationFailedRejection)
    }

    "[user JWT] create: JWT overrides body oid; ?oid= must match JWT" in {
      val jwt490 = createJwtToken("490")

      // ?oid=530 with JWT 490 -> rejected
      val r = withAuth(jwt490)(Post("/config?oid=530", WorkflowConfigCreateReq(sid = 0, name = Some("x"), oid = Some("530")))) ~>
        routes.routes
      r.rejections should contain(AuthorizationFailedRejection)

      // body oid=530 but ?oid=490 -> authorized; JWT stamps oid=490
      withAuth(jwt490)(Post("/config?oid=490", WorkflowConfigCreateReq(sid = 0, name = Some("user-created"), oid = Some("530"), pid = Some("p9")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          val c = responseAs[WorkflowConfig]
          c.oid shouldBe Some("490")
          c.pid shouldBe Some("p9")
        }
    }

    "[user JWT] update/delete own; cannot touch other oid (store 404 after auth)" in {
      val jwt490 = createJwtToken("490")

      withAuth(jwt490)(Put(withOid("/config/0", "490"), WorkflowConfigUpdateReq(title = Some("updated-490")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[WorkflowConfig].title shouldBe "updated-490"
        }

      // authorized as 490, but config 1 belongs to 530 -> Store 404
      withAuth(jwt490)(Put(withOid("/config/1", "490"), WorkflowConfigUpdateReq(title = Some("hack")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.NotFound
        }
    }

    "[admin JWT] may target any oid via ?oid=" in {
      val jwtAdmin = createJwtToken("admin-ignored", Seq(adminRole))
      withAuth(jwtAdmin)(Get("/config/1?oid=530")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigView].config.oid shouldBe Some("530")
      }
    }

    "[user JWT] resolve: Store validates JWT oid (foreign -> AuthorizationFailed)" in {
      val jwt490 = createJwtToken("490")
      val jwt530 = createJwtToken("530")

      // own config -> OK
      withAuth(jwt490)(Get("/config/resolve/0?type=id")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        val page = responseAs[WorkflowConfigs]
        page.total shouldBe 1L
        page.configs.head.oid shouldBe Some("490")
      }

      // other owner's config -> AuthorizationFailed (async onComplete — must `check`)
      withAuth(jwt490)(Get("/config/resolve/1?type=id")) ~> routes.routes ~> check {
        rejections should contain(AuthorizationFailedRejection)
      }

      // mixed ids including foreign oid -> AuthorizationFailed
      withAuth(jwt490)(Get("/config/resolve/0,1?type=id")) ~> routes.routes ~> check {
        rejections should contain(AuthorizationFailedRejection)
      }

      withAuth(jwt530)(Get("/config/resolve/1?type=id")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].configs.head.oid shouldBe Some("530")
      }
    }

    "[admin JWT] resolve any WorkflowConfig (oid=None to Store)" in {
      val jwtAdmin = createJwtToken("", Seq(adminRole))
      withAuth(jwtAdmin)(Get("/config/resolve/0,1?type=id")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].total should be >= 2L
      }
    }
  }

  "DetectorConfig oid/pid HTTP auth" should {

    "[admin JWT] create DetectorConfigs for any oid" in {
      val jwtAdmin = createJwtToken("", Seq(adminRole))

      withAuth(jwtAdmin)(Post("/detector/schema", DetectorSchemaCreateReq(name = "AuthDet"))) ~>
        apiRoutes ~> check { status shouldBe StatusCodes.OK }

      withAuth(jwtAdmin)(Post("/detector/config?oid=490", DetectorConfigCreateReq(name = "d-490-p1", sid = Some(0), oid = Some("490"), pid = Some("1")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          val d = responseAs[DetectorConfig]
          d.contract.tenantId shouldBe 490
          d.contract.projectId shouldBe 1
        }

      withAuth(jwtAdmin)(Post("/detector/config?oid=530", DetectorConfigCreateReq(name = "d-530-p1", sid = Some(0), oid = Some("530"), pid = Some("1")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[DetectorConfig].contract.tenantId shouldBe 530
        }
    }

    "[user JWT] list/get with matching ?oid=; reject mismatch" in {
      val jwt490 = createJwtToken("490")

      withAuth(jwt490)(Get(withOid("/detector/config", "490"))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[DetectorConfigs].configs.foreach(_.contract.tenantId shouldBe 490)
      }

      val r = withAuth(jwt490)(Get("/detector/config?oid=530")) ~> routes.routes
      r.rejections should contain(AuthorizationFailedRejection)

      val r2 = withAuth(jwt490)(Get("/detector/config")) ~> routes.routes
      r2.rejections should contain(AuthorizationFailedRejection)
    }

    "[user JWT] create stamps JWT oid; reject invalid ?oid=" in {
      val jwt490 = createJwtToken("490")

      val r = withAuth(jwt490)(Post("/detector/config?oid=530", DetectorConfigCreateReq(name = "x", sid = Some(0), oid = Some("530")))) ~>
        routes.routes
      r.rejections should contain(AuthorizationFailedRejection)

      withAuth(jwt490)(Post("/detector/config?oid=490", DetectorConfigCreateReq(name = "user-d", sid = Some(0), oid = Some("530")))) ~>
        apiRoutes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[DetectorConfig].contract.tenantId shouldBe 490
        }
    }
  }
}
