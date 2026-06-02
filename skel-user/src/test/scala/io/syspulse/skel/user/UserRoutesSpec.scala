package io.syspulse.skel.user

import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import spray.json._

import io.jvm.uuid._

import io.syspulse.skel.user.server._
import io.syspulse.skel.user.store._
import io.syspulse.skel.user.server.UserJson._
import io.syspulse.skel.auth.permissions.Permissions

class UserRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  sys.props += "GOD" -> "true"

  val store = new UserStoreMem()
  val typedSystem = ActorSystem(Behaviors.empty, "UserTestSystem")
  val registry = typedSystem.systemActorOf(UserRegistry(store), "UserRegistry")

  val routesPromise = Promise[UserRoutes]()
  val testBehavior = Behaviors.setup[Any] { context =>
    routesPromise.success(new UserRoutes(registry)(context, Config()))
    Behaviors.empty
  }
  typedSystem.systemActorOf(testBehavior, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = {
    typedSystem.terminate()
  }

  val testEmail = "alice@example.com"
  val testXid = "0xabc123"

  "UserRoutes" should {

    "return empty list (GET /)" in {
      Get("/") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Users].users shouldBe empty
      }
    }

    "create user with required email only (POST /)" in {
      val req = UserCreateReq(email = testEmail)
      Post("/", req) ~> routes.routes ~> check {
        status shouldBe StatusCodes.Created
        val user = responseAs[User]
        user.email shouldBe testEmail
        user.name shouldBe None
        user.xid shouldBe None
        user.avatar shouldBe None
        user.meta shouldBe None
        user.ts0 should be > 0L
        user.ts should be >= user.ts0
      }
    }

    "create user from JSON without optional fields (POST /)" in {
      val body = """{"email":"raw@example.com"}"""
      Post("/", HttpEntity(ContentTypes.`application/json`, body)) ~> routes.routes ~> check {
        status shouldBe StatusCodes.Created
        responseAs[User].email shouldBe "raw@example.com"
        responseAs[User].meta shouldBe None
      }
    }

    "create user with optional fields and meta (POST /)" in {
      val req = UserCreateReq(
        email = "bob@example.com",
        name = Some("Bob"),
        xid = Some(testXid),
        avatar = Some("https://example.com/a.png"),
        meta = Some(Map("role" -> "admin", "tier" -> 2)),
      )
      Post("/", req) ~> routes.routes ~> check {
        status shouldBe StatusCodes.Created
        val user = responseAs[User]
        user.email shouldBe "bob@example.com"
        user.name shouldBe Some("Bob")
        user.xid shouldBe Some(testXid)
        user.avatar shouldBe Some("https://example.com/a.png")
        user.meta shouldBe Some(Map("role" -> "admin", "tier" -> 2.0))
      }
    }

    "get user by id (GET /{id})" in {
      val createReq = UserCreateReq(email = "carol@example.com", name = Some("Carol"))
      val id =
        Post("/", createReq) ~> routes.routes ~> check {
          status shouldBe StatusCodes.Created
          responseAs[User].id
        }

      Get(s"/${id}") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val user = responseAs[User]
        user.id shouldBe id
        user.email shouldBe "carol@example.com"
        user.name shouldBe Some("Carol")
      }
    }

    "get user by xid (GET /xid/{xid})" in {
      Get(s"/xid/${testXid}") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val user = responseAs[User]
        user.xid shouldBe Some(testXid)
      }
    }

    "partially update user — only provided fields change (PUT /{id})" in {
      val createReq = UserCreateReq(
        email = "dave@example.com",
        name = Some("Dave"),
        meta = Some(Map("k" -> "v")),
      )
      val id =
        Post("/", createReq) ~> routes.routes ~> check {
          responseAs[User].id
        }

      val updateReq = UserUpdateReq(name = Some("Dave Updated"), meta = Some(Map("k" -> "v2", "n" -> 1)))
      Put(s"/${id}", updateReq) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val user = responseAs[User]
        user.email shouldBe "dave@example.com"
        user.name shouldBe Some("Dave Updated")
        user.meta shouldBe Some(Map("k" -> "v2", "n" -> 1.0))
      }
    }

    "update email (PUT /{id})" in {
      val createReq = UserCreateReq(email = "eve@example.com")
      val id =
        Post("/", createReq) ~> routes.routes ~> check {
          responseAs[User].id
        }

      Put(s"/${id}", UserUpdateReq(email = Some("eve.new@example.com"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[User].email shouldBe "eve.new@example.com"
      }
    }

    "delete user (DELETE /{id})" in {
      val createReq = UserCreateReq(email = "frank@example.com")
      val id =
        Post("/", createReq) ~> routes.routes ~> check {
          responseAs[User].id
        }

      Delete(s"/${id}") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[UserActionRes].status shouldBe "200"
        responseAs[UserActionRes].uid shouldBe Some(id)
      }

      Get(s"/${id}") ~> routes.routes ~> check {
        status.intValue() should (be(404) or be(500))
      }
    }

    "reject duplicate id on create (POST /)" in {
      val id = UUID.random
      val req1 = UserCreateReq(email = "g1@example.com", uid = Some(id))
      Post("/", req1) ~> routes.routes ~> check {
        status shouldBe StatusCodes.Created
      }

      val req2 = UserCreateReq(email = "g2@example.com", uid = Some(id))
      Post("/", req2) ~> routes.routes ~> check {
        status.intValue() should (be(400) or be(500))
      }
    }

    "page users with from and size query parameters (GET /?from=&size=)" in {
      val before =
        Get("/") ~> routes.routes ~> check {
          responseAs[Users].users.size
        }

      (1 to 3).foreach { i =>
        Post("/", UserCreateReq(email = s"page-$i@example.com")) ~> routes.routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }

      Get(s"/?from=${before}&size=2") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Users].users.size shouldBe 2
      }

      Get(s"/?from=${before + 3}&size=2") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Users].users shouldBe empty
      }
    }

    "search users with GET query parameter (GET /?search=)" in {
      val needle = "route-get-needle"
      val id =
        Post("/", UserCreateReq(email = s"${needle}@example.com", name = Some("Route Search"))) ~> routes.routes ~> check {
          status shouldBe StatusCodes.Created
          responseAs[User].id
        }

      Get(Uri("/").withQuery(Uri.Query("search" -> s"'${needle}'"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val users = responseAs[Users].users
        users.map(_.id) should contain(id)
      }

      Get(Uri("/").withQuery(Uri.Query("search" -> "ab"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Users].users shouldBe empty
      }
    }

    "search users with POST body (POST /search)" in {
      val needle = "route-post-needle"
      val id =
        Post("/", UserCreateReq(email = "post-search@example.com", xid = Some(s"XID-${needle}"))) ~> routes.routes ~> check {
          status shouldBe StatusCodes.Created
          responseAs[User].id
        }

      Post("/search", UserSearchReq(needle.toUpperCase)) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val users = responseAs[Users].users
        users.map(_.id) should contain(id)
      }
    }

    "page search results with from and size (GET /?search=&from=&size=)" in {
      val tag = "route-search-page"
      (1 to 3).foreach { i =>
        Post("/", UserCreateReq(email = s"${tag}-$i@example.com")) ~> routes.routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }

      Get(Uri("/").withQuery(Uri.Query("search" -> tag, "from" -> "0", "size" -> "2"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Users].users.size shouldBe 2
      }

      Get(Uri("/").withQuery(Uri.Query("search" -> tag, "from" -> "2", "size" -> "2"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Users].users.size shouldBe 1
      }
    }

    "page search results with from and size (POST /search)" in {
      val tag = "route-search-post-page"
      (1 to 4).foreach { i =>
        Post("/", UserCreateReq(email = s"${tag}-$i@example.com")) ~> routes.routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }

      Post("/search", UserSearchReq(query = tag, from = Some(1), size = Some(2))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Users].users.size shouldBe 2
      }
    }
  }
}
