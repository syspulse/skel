package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest

import spray.json._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.event._

class EventRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import EventJson._

  val store = new WorkflowStoreMem()
  val engine = new StubEngine
  val events = new EventStoreMem
  val typedSystem = ActorSystem(Behaviors.empty, "EventRouteSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine, events), "WorkflowRegistry")

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

  val jwtSecret = "secret1"
  val jwtAlgo = JwtAlgorithm.HS256
  AuthJwt(s"${jwtAlgo}://${jwtSecret}")

  def createJwt(oid: String, roles: Seq[String]): String = {
    val claims = JwtClaim(
      issuer = Some("test"),
      subject = Some("u"),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      expiration = Some((System.currentTimeMillis() / 1000) + 3600),
      content = s"""{"oid":"$oid","groups":["${roles.mkString("\",\"")}"],"tenantId":"$oid"}"""
    )
    Jwt.encode(claims, jwtSecret, jwtAlgo)
  }

  def withAuth(jwt: String)(req: akka.http.scaladsl.model.HttpRequest) =
    req ~> addHeader(Authorization(OAuth2BearerToken(jwt)))

  val adminJwtTok = createJwt("", Seq(config.adminRole))
  val userJwt490 = createJwt("490", Seq("extractor-user"))

  def ev(
    eid: String,
    oid: Long = 490L,
    pid: Long = 2141L,
    did: Long = 22587L,
    ts: Long = 1606311430006L,
    sev: Double = 0.25,
    sid: String = "WORKFLOW",
    desc: String = "hello",
    nid: String = "SafeMultisigMonitor",
    name: String = "Safe Multisig Monitor",
    rid: String = "safe:0xabc",
    wid: Option[String] = Some("wf-1"),
    tags: Option[Seq[String]] = None,
  ): EventCreateReq = EventCreateReq(
    ts = ts, eid = eid, rid = Some(rid), oid = oid, pid = pid, did = did,
    nid = nid, name = Some(name), wid = wid, sid = Some(sid), sev = sev, desc = Some(desc),
    meta = Some(JsObject("method" -> JsString("withdraw"))),
    tags = tags,
  )

  "POST /event" should {
    "create a single Event stored as Alert fields and GET by Elastic key and eid" in {
      withAuth(adminJwtTok)(Post("/event", ev("e-one", tags = Some(Seq("COMPLIANCE"))))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[Alerts]
        r.total shouldBe 1L
        val a = r.events.head
        a.id shouldBe "22587:e-one"
        a.eid shouldBe "e-one"
        a.tx shouldBe Some("safe:0xabc")
        a.teid shouldBe 490L
        a.prid shouldBe 2141L
        a.deid shouldBe 22587L
        a.sna shouldBe "SafeMultisigMonitor"
        a.ana shouldBe "Safe Multisig Monitor"
        a.sid shouldBe "WORKFLOW"
        a.nse shouldBe 0.25
        a.se shouldBe "MEDIUM"
        a.dt shouldBe Seq("COMPLIANCE")
        a.ame shouldBe "hello"
        a.wid shouldBe Some("wf-1")
        a.meta.get.fields("method") shouldBe JsString("withdraw")
      }

      withAuth(adminJwtTok)(Get("/event/22587:e-one")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Alert].eid shouldBe "e-one"
      }

      withAuth(adminJwtTok)(Get("/event/eid/e-one")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Alerts].events.head.id shouldBe "22587:e-one"
      }
    }

    "derive se from sev (inclusive thresholds); NONE is empty" in {
      def seOf(sev: Double): String = Alert.fromCreate(ev("e-se", sev = sev)).se
      seOf(0.75) shouldBe "CRITICAL"
      seOf(1.0) shouldBe "CRITICAL"
      seOf(0.5) shouldBe "HIGH"
      seOf(0.25) shouldBe "MEDIUM"
      seOf(0.15) shouldBe "LOW"
      seOf(0.1) shouldBe "INFO"
      seOf(0.0) shouldBe ""
      seOf(0.09) shouldBe ""
    }

    "store optional tags as Alert dt" in {
      withAuth(adminJwtTok)(Post("/event", ev("e-tags", tags = Some(Seq(" COMPLIANCE ", "AUDIT"))))) ~> apiRoutes ~> check {
        responseAs[Alerts].events.head.dt shouldBe Seq("COMPLIANCE", "AUDIT")
      }
    }

    "create multiple Events per request" in {
      val body = Seq(ev("e-m1", did = 1), ev("e-m2", did = 2)).toJson
      withAuth(adminJwtTok)(Post("/event").withEntity(akka.http.scaladsl.model.HttpEntity(akka.http.scaladsl.model.ContentTypes.`application/json`, body.compactPrint))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Alerts].total shouldBe 2L
      }
    }

    "overwrite the previous object when the same eid (and did) is posted" in {
      withAuth(adminJwtTok)(Post("/event", ev("e-ow", ts = 1000L, desc = "v1"))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
      }
      withAuth(adminJwtTok)(Post("/event", ev("e-ow", ts = 2000L, desc = "v2"))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        val a = responseAs[Alerts].events.head
        a.ts shouldBe 2000L
        a.ame shouldBe "v2"
        a.id shouldBe "22587:e-ow"
      }
      withAuth(adminJwtTok)(Get("/event/22587:e-ow")) ~> apiRoutes ~> check {
        responseAs[Alert].ame shouldBe "v2"
        responseAs[Alert].ts shouldBe 2000L
      }
    }

    "default sid to WORKFLOW when omitted" in {
      val req = ev("e-sid").copy(sid = None)
      withAuth(adminJwtTok)(Post("/event", req)) ~> apiRoutes ~> check {
        responseAs[Alerts].events.head.sid shouldBe "WORKFLOW"
      }
    }

    "reject non-numeric oid in JSON body" in {
      val bad = """{"ts":1,"eid":"e-bad","oid":"abc","pid":1,"did":1,"nid":"N","sev":0.1}"""
      withAuth(adminJwtTok)(Post("/event").withEntity(akka.http.scaladsl.model.HttpEntity(akka.http.scaladsl.model.ContentTypes.`application/json`, bad))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "keep two Alerts when the same eid is posted with different did" in {
      withAuth(adminJwtTok)(Post("/event", Seq(ev("e-dup", did = 10L), ev("e-dup", did = 11L)).toJson)) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Alerts].total shouldBe 2L
      }
      withAuth(adminJwtTok)(Get("/event/eid/e-dup")) ~> apiRoutes ~> check {
        responseAs[Alerts].events.map(_.id).toSet shouldBe Set("10:e-dup", "11:e-dup")
      }
    }
  }

  "GET /event query" should {
    "filter by ts range, oid, pid, did, sid and page" in {
      withAuth(adminJwtTok)(Post("/event", Seq(
        ev("q1", oid = 10, pid = 1, did = 100, ts = 1000, sid = "WORKFLOW"),
        ev("q2", oid = 10, pid = 1, did = 100, ts = 2000, sid = "WORKFLOW"),
        ev("q3", oid = 10, pid = 2, did = 100, ts = 3000, sid = "OTHER"),
        ev("q4", oid = 11, pid = 1, did = 100, ts = 4000, sid = "WORKFLOW"),
      ).toJson)) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
      }

      withAuth(adminJwtTok)(Get("/event?oid=10&ts0=1000&ts1=2500")) ~> apiRoutes ~> check {
        val r = responseAs[Alerts]
        r.events.map(_.eid).toSet shouldBe Set("q1", "q2")
      }

      withAuth(adminJwtTok)(Get("/event?oid=10&pid=2")) ~> apiRoutes ~> check {
        responseAs[Alerts].events.map(_.eid) shouldBe Seq("q3")
      }

      withAuth(adminJwtTok)(Get("/event?oid=10&did=100&sid=WORKFLOW")) ~> apiRoutes ~> check {
        responseAs[Alerts].events.map(_.eid).toSet shouldBe Set("q1", "q2")
      }

      withAuth(adminJwtTok)(Get("/event?oid=10&from=0&size=1")) ~> apiRoutes ~> check {
        val r = responseAs[Alerts]
        r.total shouldBe 3L // q1,q2,q3
        r.events should have size 1
      }
    }
  }

  "DELETE /event" should {
    "delete by Elastic key and by eid" in {
      withAuth(adminJwtTok)(Post("/event", Seq(ev("d-key"), ev("d-eid")).toJson)) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
      }

      withAuth(adminJwtTok)(Delete("/event/22587:d-key")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[EventActionRes].status shouldBe EventActionRes.OK
      }
      withAuth(adminJwtTok)(Get("/event/22587:d-key")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.NotFound
      }

      withAuth(adminJwtTok)(Delete("/event/eid/d-eid")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
      }
      withAuth(adminJwtTok)(Get("/event/eid/d-eid")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "delete by eid query parameter" in {
      withAuth(adminJwtTok)(Post("/event", ev("d-q"))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
      }
      withAuth(adminJwtTok)(Delete("/event?eid=d-q")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[EventActionRes].status shouldBe EventActionRes.OK
      }
      withAuth(adminJwtTok)(Get("/event/eid/d-q")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  "Event oid auth" should {
    "let a user create/query only their oid and hide others" in {
      withAuth(adminJwtTok)(Post("/event", ev("auth-other", oid = 999L))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
      }
      withAuth(userJwt490)(Post("/event?oid=490", ev("auth-mine", oid = 490L))) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Alerts].events.head.teid shouldBe 490L
      }
      withAuth(userJwt490)(Get("/event?oid=490&size=100")) ~> apiRoutes ~> check {
        val eids = responseAs[Alerts].events.map(_.eid)
        eids should contain("auth-mine")
        eids should not contain "auth-other"
      }
      withAuth(userJwt490)(Get("/event/22587:auth-other?oid=490")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "reject user without oid" in {
      withAuth(userJwt490)(Get("/event")) ~> apiRoutes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }
}
