package io.syspulse.skel.enroll.server

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
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.Command

import scala.concurrent.Await
import scala.concurrent.duration.Duration

//import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.enroll._
import io.syspulse.skel.enroll.Config
import io.syspulse.skel.enroll.store.EnrollRegistry._


@Path("/api/v1/enroll")
class EnrollRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable { 
  //with RouteAuthorizers {
  val log = Logger(s"${this}")
  
  implicit val system: ActorSystem[_] = context.system
  
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import EnrollJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricDeleteCount: Counter = Counter.build().name("skel_enroll_delete_total").help("Enroll deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_enroll_create_total").help("Enroll creates").register(cr)
  
  def getEnrolls(): Future[Enrolls] = registry.ask(GetEnrolls)
  def getEnroll(id: UUID): Future[Option[Enroll]] = registry.ask(GetEnroll(id, _))
  def getEnrollByEmail(email: String): Future[Option[Enroll]] = registry.ask(GetEnrollByEmail(email, _))

  def createEnroll(enrollCreate: Option[EnrollCreateReq]): Future[EnrollActionRes] = registry.ask(CreateEnroll(enrollCreate, _))
  def updateEnroll(enrollUpdate: EnrollUpdateReq): Future[Option[Enroll]] = registry.ask(UpdateEnroll(enrollUpdate, _))
  
  def deleteEnroll(id: UUID): Future[EnrollActionRes] = registry.ask(DeleteEnroll(id, _))
  

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Return Enroll by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Enroll id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Enroll returned",content=Array(new Content(schema=new Schema(implementation = classOf[Enroll])))))
  )
  def getEnrollRoute(id: String) = get {
    rejectEmptyResponse {
      
      onSuccess(getEnroll(UUID.fromString(id))) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/email/{email}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Get Enroll by Email (email)",
    parameters = Array(new Parameter(name = "email", in = ParameterIn.PATH, description = "email")),
    responses = Array(new ApiResponse(responseCode="200",description = "Enroll returned",content=Array(new Content(schema=new Schema(implementation = classOf[Enroll])))))
  )
  def getEnrollByEmailRoute(email: String) = get {
    rejectEmptyResponse {
      onSuccess(getEnrollByEmail(email)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"), summary = "Return all Enrolls",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Enrolls",content = Array(new Content(schema = new Schema(implementation = classOf[Enrolls])))))
  )
  def getEnrollsRoute() = get {
    complete(getEnrolls())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Delete Enroll by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Enroll id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Enroll deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Enroll])))))
  )
  def deleteEnrollRoute(id: String) = delete {
    onSuccess(deleteEnroll(UUID.fromString(id))) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Create (Start) Enroll Flow",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[EnrollCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Enroll created",content = Array(new Content(schema = new Schema(implementation = classOf[EnrollActionRes])))))
  )
  def createEnrollRoute = post {
    entity(as[Option[EnrollCreateReq]]) { enrollCreate =>
      onSuccess(createEnroll(enrollCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  @POST @Path("/{id}/email") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Add email to Enroll",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[EnrollUpdateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Enroll updated with Email",content = Array(new Content(schema = new Schema(implementation = classOf[Enroll])))))
  )
  def updateEnrollEmailRoute(id: String) = post {
    entity(as[EnrollUpdateReq]) { enrollUpdate =>
      onSuccess(updateEnroll(enrollUpdate.copy(id = UUID.fromString(id),command=Some("email")))) { r => 
        complete((StatusCodes.OK, r))
      }
    }
  }

  @GET @Path("/{id}/confirm/{code}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Confirm Email with Confirmation Code",
    responses = Array(new ApiResponse(responseCode = "200", description = "Email confirmed",content = Array(new Content(schema = new Schema(implementation = classOf[Enroll])))))
  )
  def getEnrollEmailConfirmRoute(id: String,code:String) = get {
    onSuccess(updateEnroll(EnrollUpdateReq(id = UUID.fromString(id),command=Some("confirm"),data=Map("code"->code)))) { r => 
      complete((StatusCodes.OK, r))
    }
    
  }

  @POST @Path("/{id}/confirm") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Confirm Email with Confirmation Code",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[EnrollUpdateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Email confirmed",content = Array(new Content(schema = new Schema(implementation = classOf[Enroll])))))
  )
  def updateEnrollEmailConfirmRoute = post {
    entity(as[EnrollUpdateReq]) { enrollUpdate =>
      onSuccess(updateEnroll(enrollUpdate.copy(command=Some("confirm")))) { r => 
        complete((StatusCodes.OK, r))
      }
    }
  }

  override def routes: Route =
      concat(
        pathEndOrSingleSlash {
          concat(
            //authenticate()(authn =>
            //  authorize(Permissions.isAdmin(authn)) {              
                getEnrollsRoute() ~                
                createEnrollRoute  
            //  }
            //),            
          )
        },
        
        pathPrefix("email") {
          pathPrefix(Segment) { xid => 
            getEnrollByEmailRoute(xid)
          }
        },
        pathPrefix(Segment) { id => {
            pathPrefix("email") {
              pathEndOrSingleSlash {
                updateEnrollEmailRoute(id)                
              }
            } ~
            pathPrefix("confirm") {
              pathPrefix(Segment) { code => {                
                  getEnrollEmailConfirmRoute(id,code)
                }
              } ~
              pathEndOrSingleSlash {
                updateEnrollEmailConfirmRoute
              }              
            } ~
            pathEndOrSingleSlash {
              //authenticate()(authn =>
              //  authorize(Permissions.isEnroll(UUID(id),authn)) {
                  getEnrollRoute(id) ~
              //  } ~
              //  authorize(Permissions.isAdmin(authn)) {
                  deleteEnrollRoute(id)
              //  }
              //) 
            }
          }
        }
      )
    
}
