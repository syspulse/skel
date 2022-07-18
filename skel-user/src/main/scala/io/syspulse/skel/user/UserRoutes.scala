package io.syspulse.skel.user

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.StatusCodes._
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import javax.ws.rs.core.MediaType

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.user.UserRegistry._
import io.syspulse.skel.Command

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import io.syspulse.skel.auth.permissions.rbac.Permissions
import io.syspulse.skel.auth.RouteAuthorizers


@Path("/api/v1/user")
class UserRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import UserJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_user_get_total").help("User gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_user_delete_total").help("User deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_user_create_total").help("User creates").register(cr)
  
  def getUsers(): Future[Users] = registry.ask(GetUsers)
  def getUser(id: UUID): Future[Option[User]] = registry.ask(GetUser(id, _))
  def getUserByEid(eid: String): Future[Option[User]] = registry.ask(GetUserByEid(eid, _))

  def createUser(userCreate: UserCreateReq): Future[User] = registry.ask(CreateUser(userCreate, _))
  def deleteUser(id: UUID): Future[UserActionRes] = registry.ask(DeleteUser(id, _))
  def randomUser(): Future[User] = registry.ask(RandomUser(_))


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Return User by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "User id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "User returned",content=Array(new Content(schema=new Schema(implementation = classOf[User])))))
  )
  def getUserRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getUser(UUID.fromString(id))) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }


  // @GET @Path("/{id}/code") @Produces(Array(MediaType.APPLICATION_JSON))
  // @Operation(tags = Array("user"),summary = "Get User code by id",
  //   parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "User id (uuid)")),
  //   responses = Array(new ApiResponse(responseCode="200",description = "User Code returned",content=Array(new Content(schema=new Schema(implementation = classOf[UserCode])))))
  // )
  // def getUserCodeRoute(id: String) = get {
  //   rejectEmptyResponse {
  //     onSuccess(getUserCode(UUID.fromString(id))) { r =>
  //       metricGetCodeCount.inc()
  //       complete(r)
  //     }
  //   }
  // }

  @GET @Path("/eid/{eid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Get User by External Id (eid)",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "eid")),
    responses = Array(new ApiResponse(responseCode="200",description = "User returned",content=Array(new Content(schema=new Schema(implementation = classOf[User])))))
  )
  def getUserByEidRoute(eid: String) = get {
    rejectEmptyResponse {
      onSuccess(getUserByEid(eid)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"), summary = "Return all Users",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Users",content = Array(new Content(schema = new Schema(implementation = classOf[Users])))))
  )
  def getUsersRoute() = get {
    metricGetCount.inc()
    complete(getUsers())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Delete User by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "User id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "User deleted",content = Array(new Content(schema = new Schema(implementation = classOf[User])))))
  )
  def deleteUserRoute(id: String) = delete {
    onSuccess(deleteUser(UUID.fromString(id))) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Create User Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[UserCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "User created",content = Array(new Content(schema = new Schema(implementation = classOf[UserActionRes])))))
  )
  def createUserRoute = post {
    entity(as[UserCreateReq]) { userCreate =>
      onSuccess(createUser(userCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  def createUserRandomRoute() = post { 
    onSuccess(randomUser()) { r =>
      metricCreateCount.inc()
      complete((StatusCodes.Created, r))
    }
  }

  override def routes: Route =
      concat(
        pathEndOrSingleSlash {
          // authenticate()(authn =>
          //   authorize(Permissions.isAdmin(authn)) {
          //     concat(
          //       getUsersRoute(),
          //       createUserRoute
          //     )
          //   }
          // )
          concat(
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {              
                getUsersRoute() ~                
                createUserRoute  
              }
            ),
            //createUserRoute
          )
        },
        // pathPrefix("info") {
        //   path(Segment) { userId => 
        //     getUserInfo(userId)
        //   }
        // },
        pathSuffix("random") {
          createUserRandomRoute()
        },
        pathPrefix("eid") {
          pathPrefix(Segment) { id => 
            getUserByEidRoute(id)
          }
        },
        pathPrefix(Segment) { id => 
          // pathPrefix("eid") {
          //   pathEndOrSingleSlash {
          //     getUserByEidRoute(id)
          //   } 
          //   ~
          //   path(Segment) { code =>
          //     getUserCodeVerifyRoute(id,code)
          //   }
          // } ~

          pathEndOrSingleSlash {
            // concat(
            //   getUserRoute(id),
            //   deleteUserRoute(id),
            // )          
            authenticate()(authn =>
              authorize(Permissions.isUser(UUID(id),authn)) {
                getUserRoute(id)
              } ~
              authorize(Permissions.isAdmin(authn)) {
                deleteUserRoute(id)
              }
            ) 
          }
        }
      )
    
}
