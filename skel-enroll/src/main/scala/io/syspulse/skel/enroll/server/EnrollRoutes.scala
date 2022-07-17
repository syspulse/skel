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
import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import javax.ws.rs.core.MediaType

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
  def getEnrollByXid(xid: String): Future[Option[Enroll]] = registry.ask(GetEnrollByXid(xid, _))

  def createEnroll(enrollCreate: EnrollCreateReq): Future[EnrollActionRes] = registry.ask(CreateEnroll(enrollCreate, _))
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
      // onSuccess(EnrollSystem.summaryFuture(UUID(id))) { r =>
      //   complete(r.map( e => Enroll(e.eid,e.email.getOrElse(""),"",e.xid.getOrElse(""),e.tsPhase)))
      // }
    }
  }


  // @GET @Path("/{id}/code") @Produces(Array(MediaType.APPLICATION_JSON))
  // @Operation(tags = Array("enroll"),summary = "Get Enroll code by id",
  //   parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Enroll id (uuid)")),
  //   responses = Array(new ApiResponse(responseCode="200",description = "Enroll Code returned",content=Array(new Content(schema=new Schema(implementation = classOf[EnrollCode])))))
  // )
  // def getEnrollCodeRoute(id: String) = get {
  //   rejectEmptyResponse {
  //     onSuccess(getEnrollCode(UUID.fromString(id))) { r =>
  //       complete(r)
  //     }
  //   }
  // }

  @GET @Path("/eid/{eid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("enroll"),summary = "Get Enroll by External Id (eid)",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "eid")),
    responses = Array(new ApiResponse(responseCode="200",description = "Enroll returned",content=Array(new Content(schema=new Schema(implementation = classOf[Enroll])))))
  )
  def getEnrollByXidRoute(eid: String) = get {
    rejectEmptyResponse {
      onSuccess(getEnrollByXid(eid)) { r =>
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
  @Operation(tags = Array("enroll"),summary = "Create Enroll Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[EnrollCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Enroll created",content = Array(new Content(schema = new Schema(implementation = classOf[EnrollActionRes])))))
  )
  def createEnrollRoute = post {
    entity(as[EnrollCreateReq]) { enrollCreate =>
      onSuccess(createEnroll(enrollCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
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
        // pathPrefix("info") {
        //   path(Segment) { enrollId => 
        //     getEnrollInfo(enrollId)
        //   }
        // },
        
        pathPrefix("xid") {
          pathPrefix(Segment) { xid => 
            getEnrollByXidRoute(xid)
          }
        },
        pathPrefix(Segment) { id => 
          // pathPrefix("eid") {
          //   pathEndOrSingleSlash {
          //     getEnrollByEidRoute(id)
          //   } 
          //   ~
          //   path(Segment) { code =>
          //     getEnrollCodeVerifyRoute(id,code)
          //   }
          // } ~

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
      )
    
}
