package io.syspulse.skel.dash

import org.scalatest.{BeforeAndAfterAll}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import spray.json._

import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm

import io.syspulse.skel.dash.server._
import io.syspulse.skel.dash.store._
import io.syspulse.skel.dash.server.DashJson._
import scala.concurrent.Promise
import io.syspulse.skel.auth.jwt.AuthJwt
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection
import io.syspulse.skel.dash.source.DataSourceTest
import io.syspulse.skel.dash.Config
import io.syspulse.skel.db.guard.QueryGuardAllow

class DashRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll { 
  
  implicit val config: Config = Config(
    ownerAttr = "tenantId",
    rolesAttr = "groups[].",
    serviceRole = "extractor-service",
    adminRole = "extractor-admin",
    permissions = "user",
  )

  val store = new DashStoreMem()
  val ds = new DataSourceTest("test://", QueryGuardAllow)  
  val typedSystem = akka.actor.typed.ActorSystem(Behaviors.empty, "DashTestSystem")
  val registry = typedSystem.systemActorOf(DashRegistry(store, ds), "DashRegistry")
  
  val routesPromise = Promise[DashRoutes]()
  
  val testBehavior = Behaviors.setup[Any] { context =>    
    val routes = new DashRoutes(registry)(context, config)    
    routesPromise.success(routes)
    Behaviors.empty
  }
  val testActor = typedSystem.systemActorOf(testBehavior, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)
    
  // Cleanup after tests
  override def afterAll(): Unit = {
    
  }
    
  // JWT secret for testing
  val jwtSecret = "secret1"
  val jwtAlgo = JwtAlgorithm.HS256  
  AuthJwt(s"${jwtAlgo}://${jwtSecret}")

  val userRole = "extractor-user"
  val adminRole = "extractor-admin"
  val serviceRole = "extractor-service"
  
  // Create JWT token with tenantId claim
  def createJwtToken(tenantId: String, roles: Seq[String] = Seq("extractor-user")): String = {
    val claims = JwtClaim(
      issuer = Some("test"),
      subject = Some("test-user"),
      audience = Some(Set("test")),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      expiration = Some((System.currentTimeMillis() / 1000) + 3600), // 1 hour
      content = s"""{"tenantId":"$tenantId","groups":["${roles.mkString("\",\"")}"]}"""
    )

    Jwt.encode(claims, jwtSecret, jwtAlgo)
  }
  
  "DashRoutes HTTP endpoints" should {
    
    "[user] create dash" in {
      // Create JWT token with tenantId
      val jwt1 = createJwtToken("400", Seq(userRole))
            
      // Create minimal dash request
      val dashRequest = DashCreateReq(
        layout = JsObject("widgets" -> JsArray()),
        name = Some("Test Dashboard 1"),
        desc = None,
        tags = None,
        pid = Some("100"),
        tid = Some("400")
      )
      
      // Test HTTP POST request to create dash
      Post("/400/100", dashRequest) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt1))) ~>
        addHeader("Content-Type", "application/json") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("id")

          val r = responseAs[DashRes]
          val id1 = r.id
          info(s"id: ${id1}")
          id1 should not be empty

          val jwt2 = createJwtToken("400", Seq(userRole))

          Get(s"/400/100/${id1}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwt2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
              val r2 = responseAs[DashRes]
              val id2 = r2.id
              info(s"id: ${id2}")
              id2 should not be empty
              id2 shouldBe id1
            }
        }
    }
    
    "[user] deny create dash in different tenant" in {
      // Create JWT token with different tenant
      val jwtToken = createJwtToken("500", Seq(userRole))      
      
      // Create dash request for different tenant
      val dashRequest = DashCreateReq(
        layout = JsObject("widgets" -> JsArray()),
        name = Some("Test Dashboard 2"),
        desc = None,
        tags = None,
        pid = Some("100"),
        tid = Some("400") // Requesting different tenant
      )
      
      val r = Post("/400/100", dashRequest) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtToken))) ~>
        addHeader("Content-Type", "application/json") ~>
        routes.routes        
      r.rejections should contain(AuthorizationFailedRejection)
    }
    
    "[admin] allow admin to create in any tenant" in {
      // Create JWT token with admin role
      val jwtAdmin = createJwtToken("999", Seq(adminRole))
      
      val dashRequest = DashCreateReq(
        layout = JsObject("widgets" -> JsArray()),
        name = Some("Admin Dashboard"),
        desc = None,
        tags = None,
        pid = Some("100"),
        tid = Some("400")
      )
      
      Post("/400/100", dashRequest) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin))) ~>
        addHeader("Content-Type", "application/json") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("id")

          // allow user from this tenant to GET          
          val r = responseAs[DashRes]
          val id = r.id
          info(s"id: ${id}")
          id should not be empty

          val jwt1 = createJwtToken("400", Seq(userRole))

          // Owner can GET
          Get(s"/400/100/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwt1))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }

          val dashUpdate1 = DashUpdateReq(
            layout = Some(JsObject("widgets" -> JsArray())),
            name = Some("Test Dashboard 400 Updated"),
            pid = Some("100"),
            tid = Some("400")
          )
          Put(s"/400/100/${id}",dashUpdate1) ~>
            addHeader(Authorization(OAuth2BearerToken(jwt1))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }

          Delete(s"/400/100/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwt1))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }
        }
    }
    
    "[service] allow service account to create in any tenant" in {
      // Create JWT token with admin role
      val jwtToken = createJwtToken("777", Seq(serviceRole))
      
      val dashRequest = DashCreateReq(
        layout = JsObject("widgets" -> JsArray()),
        name = Some("User Dashboard"),
        desc = None,
        tags = None,
        pid = Some("100"),
        tid = Some("400")
      )
      
      Post("/400/100", dashRequest) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtToken))) ~>
        addHeader("Content-Type", "application/json") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("id")
        }
    }
    
    "reject request without JWT token" in {
      val dashRequest = DashCreateReq(
        layout = JsObject("widgets" -> JsArray()),
        name = Some("Unauthorized Dashboard"),
        desc = None,
        tags = None,
        pid = Some("100"),
        tid = Some("400")
      )
      
      val r = Post("/400/100", dashRequest) ~>
        addHeader("Content-Type", "application/json") ~>
        routes.routes

      r.rejections.head === (AuthenticationFailedRejection.CredentialsMissing.getClass)
    }
    

    "[user] reject operations from different tenantId" in {
      val jwt1 = createJwtToken("400", Seq(userRole))      
      
      val dashRequest = DashCreateReq(
        layout = JsObject("widgets" -> JsArray()),
        name = Some("Test Dashboard 400"),
        desc = None,
        tags = None,
        pid = Some("100"),
        tid = Some("400")
      )
      
      val r = Post("/400/100", dashRequest) ~>
        addHeader(Authorization(OAuth2BearerToken(jwt1))) ~>
        addHeader("Content-Type", "application/json") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          val r2 = responseAs[DashRes]
          val id = r2.id
          info(s"id: ${id}")
          id should not be empty

          // Owner can GET
          Get(s"/400/100/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwt1))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }

          val dashUpdate1 = DashUpdateReq(
            layout = Some(JsObject("widgets" -> JsArray())),
            name = Some("Test Dashboard 400 Updated"),
            pid = Some("100"),
            tid = Some("400")
          )
          Put(s"/400/100/${id}",dashUpdate1) ~>
            addHeader(Authorization(OAuth2BearerToken(jwt1))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }


          // ALIEN !!!
          val jwt2 = createJwtToken("500", Seq(userRole))

          // UPDATE with non-own tid
          val r3 = Get(s"/400/100/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwt2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes

          r3.rejections should contain(AuthorizationFailedRejection)

          // UPDATE with non-own tid
          val dashRequest4 = DashCreateReq(
            layout = JsObject("widgets" -> JsArray()),
            name = Some("Test Dashboard 500"),
            desc = None,
            tags = None,
            pid = Some("100"),
            tid = Some("400")
          )

          val r4 = Post(s"/400/100/${id}",dashRequest4) ~>
            addHeader(Authorization(OAuth2BearerToken(jwt2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes

          r4.rejections should contain(AuthorizationFailedRejection)


          // UPDATE with own tid
          val dashRequest5 = DashCreateReq(
            layout = JsObject("widgets" -> JsArray()),
            name = Some("Test Dashboard 500"),
            desc = None,
            tags = None,
            pid = Some("100"),
            tid = Some("500")
          )

          val r5 = Post(s"/400/100/${id}",dashRequest5) ~>
            addHeader(Authorization(OAuth2BearerToken(jwt2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes

          r5.rejections should contain(AuthorizationFailedRejection)

          // DELETE with non-own tid
          val r6 = Delete(s"/400/100/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwt2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes

          r6.rejections should contain(AuthorizationFailedRejection)
        }            
    }

    "[admin] admin-2 can acccess Dash from admin-1" in {
      // Create JWT token with admin role
      val admin1 = "490"
      val admin2 = "882"
      val jwtAdmin1 = createJwtToken(admin1, Seq(adminRole))
      val jwtAdmin2 = createJwtToken(admin2, Seq("extractor-user","default-roles-hacken","extractor-manager",adminRole))

      val pid1 = "3099"
      
      val dashRequest = DashCreateReq(
        layout = JsObject("widgets" -> JsArray()),
        name = Some("Admin-1 Dashboard"),
        desc = None,
        tags = None,
        pid = Some(pid1),
        tid = Some(admin1)
      )
      
      Post(s"/$admin1/$pid1", dashRequest) ~>
        addHeader(Authorization(OAuth2BearerToken(jwtAdmin1))) ~>
        addHeader("Content-Type", "application/json") ~>
        routes.routes ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("id")

          // allow user from this tenant to GET          
          val r = responseAs[DashRes]
          val id = r.id
          info(s"id: ${id}")
          id should not be empty
          
          // Admin-1 can GET
          Get(s"/$admin1/$pid1/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwtAdmin1))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }

          // Admin-2 can GET
          Get(s"/$admin1/$pid1/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwtAdmin2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }

          val dashUpdate1 = DashUpdateReq(
            layout = Some(JsObject("widgets" -> JsArray())),
            name = Some("Test Dashboard Admin-1 Updated"),
            pid = Some(pid1),
            tid = Some(admin1)
          )
          Put(s"/$admin1/$pid1/${id}",dashUpdate1) ~>
            addHeader(Authorization(OAuth2BearerToken(jwtAdmin2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }

          Delete(s"/$admin1/$pid1/${id}") ~>
            addHeader(Authorization(OAuth2BearerToken(jwtAdmin2))) ~>
            addHeader("Content-Type", "application/json") ~>
            routes.routes ~>
            check {
              status shouldBe StatusCodes.OK
            }
        }
    }    
  }
   
}