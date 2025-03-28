package io.syspulse.skel.user.server

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import java.nio.file.Paths

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

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

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType


import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes

import io.syspulse.skel.Command

import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.user._
import io.syspulse.skel.user.store.UserRegistry
import io.syspulse.skel.user.store.UserRegistry._
import io.syspulse.skel.user.server.{UserActionRes, Users, UserCreateReq, UserUpdateReq}
import io.syspulse.skel.service.telemetry.TelemetryRegistry
import com.typesafe.config.ConfigFactory

import akka.actor.typed.DispatcherSelector
import scala.concurrent.ExecutionContextExecutor

@Path("/")
class UserRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  // val BLOCKING_DISPATCHER = "blocking-dispatcher"
  // implicit val blockingDispatcher: ExecutionContextExecutor = try {
  //   system.dispatchers.lookup(DispatcherSelector.fromConfig(BLOCKING_DISPATCHER))  
  // } catch {
  //   case _: Exception => 
  //     log.warn(s"dispatcher not found: ${BLOCKING_DISPATCHER}")
  //     system.dispatchers.lookup(DispatcherSelector.default())
  // }
  
  implicit val permissions = Permissions()

  import io.syspulse.skel.user.store.UserRegistryProto._

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import UserJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  // val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_user_get_total").help("User gets").register(TelemetryRegistry.registry)
  val metricDeleteCount: Counter = Counter.build().name("skel_user_delete_total").help("User deletes").register(TelemetryRegistry.registry)
  val metricCreateCount: Counter = Counter.build().name("skel_user_create_total").help("User creates").register(TelemetryRegistry.registry)
  val metricUpdateCount: Counter = Counter.build().name("skel_user_update_total").help("User updates").register(TelemetryRegistry.registry)
  
  def getUsers(): Future[Users] = registry.ask(GetUsers)
  def getUser(id: UUID): Future[Try[User]] = registry.ask(GetUser(id, _))
  def getUserByXid(xid: String): Future[Option[User]] = registry.ask(GetUserByXid(xid, _))

  def createUser(req: UserCreateReq): Future[Try[User]] = registry.ask(CreateUser(req, _))
  def updateUser(uid:UUID,req: UserUpdateReq): Future[Try[User]] = registry.ask(UpdateUser(uid,req, _))
  def deleteUser(id: UUID): Future[UserActionRes] = registry.ask(DeleteUser(id, _))
  def randomUser(): Future[User] = registry.ask(RandomUser(_))

  def testTimeout(delay: Long): Future[UserActionRes] = registry.ask(TestTimeout(delay, _))

  @GET @Path("/timeout/{delay}") @Produces(Array(MediaType.APPLICATION_JSON))
  def getTimeoutRoute(delay: String) = get {
    rejectEmptyResponse {
      onSuccess(testTimeout(delay.toLong)) { r =>
        complete(r)
      }
    }
  }

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

  @GET @Path("/xid/{xid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Get User by External Id (xid)",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "xid")),
    responses = Array(new ApiResponse(responseCode="200",description = "User returned",content=Array(new Content(schema=new Schema(implementation = classOf[User])))))
  )
  def getUserByXidRoute(xid: String) = get {
    rejectEmptyResponse {
      onSuccess(getUserByXid(xid)) { r =>
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
      complete(StatusCodes.OK, r)
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Create User",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[UserCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "User",content = Array(new Content(schema = new Schema(implementation = classOf[User])))))
  )
  def createUserRoute = post {
    entity(as[UserCreateReq]) { req =>
      onSuccess(createUser(req)) { r =>
        metricCreateCount.inc()
        complete(StatusCodes.Created, r)
      }
    }
  }

  @PUT @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Update User",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[UserUpdateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "User",content = Array(new Content(schema = new Schema(implementation = classOf[User])))))
  )
  def updateUserRoute(uid:String) = put {
    entity(as[UserUpdateReq]) { req =>
      onSuccess(updateUser(UUID(uid),req)) { r =>
        metricUpdateCount.inc()
        complete(StatusCodes.OK, r)
      }
    }
  }

  def createUserRandomRoute() = post { 
    onSuccess(randomUser()) { r =>
      metricCreateCount.inc()
      complete(StatusCodes.Created, r)
    }
  }

  def uploadFileRoute(uid:Option[UUID],fileField:String = "fileUpload",fileName:Option[String] = None,dir:String = config.uploadStore) = 
    post {
      fileUpload(fileField) {
        case (fileInfo, fileStream) =>
          val fileName0 = fileInfo.fileName
          val ext = fileName0.split("\\.").lastOption.getOrElse("png")
          val fileName1 = fileName.getOrElse(s"${uid.orElse(Some("")).get}-${UUID.random}.${ext}")
          val fullName = s"${dir}/${fileName1}"
          val sink = FileIO.toPath(Paths.get(dir) resolve fileName1)
          val r = fileStream.runWith(sink)
          onSuccess(r) { r =>
            log.info(s"${r}: ${fileName0} -> ${fullName}: ${r.count}")
            complete(OK,UserUploadRes("success",uid,config.uploadUri + "/" + fileName1,Some(fullName)))
          }
      }
    }
  
  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
      concat(
        pathEndOrSingleSlash {
          concat(
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getUsersRoute() ~                
                createUserRoute  
              }
            ),            
          )
        },
        pathPrefix("xid") {
          pathPrefix(Segment) { xid => 
            authenticate()(authn =>
              getUserByXidRoute(xid)
            )
          }
        },
        pathPrefix("timeout") {
          pathPrefix(Segment) { delay => 
            authenticate()(authn =>
              getTimeoutRoute(delay)
            )
          }
        },
        pathPrefix(Segment) { id => 
          pathPrefix("avatar") {
            authenticate()(authn =>
              uploadFileRoute(Some(UUID(id)))
            )
          } ~
          pathEndOrSingleSlash {
            authenticate()(authn =>
              authorize(Permissions.isUser(UUID(id),authn) || Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                updateUserRoute(id) ~
                getUserRoute(id)                 
              } ~
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                deleteUserRoute(id)
              }
            ) 
          }
        }
      )
  }
}
