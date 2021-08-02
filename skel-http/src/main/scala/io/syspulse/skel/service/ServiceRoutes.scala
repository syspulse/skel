package io.syspulse.skel.service

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import akka.http.scaladsl.model._
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

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.Counter
import nl.grons.metrics4.scala.MetricName

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.ServiceRegistry._

@Path("/api/v1/service")
class ServiceRoutes(serviceRegistry: ActorRef[ServiceRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import ServiceJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("service.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("service-get-count")
  val metricPostCount: Counter = metrics.counter("service-post-count")
  val metricDeleteCount: Counter = metrics.counter("service-delete-count")


  def getServices(): Future[Services] = serviceRegistry.ask(GetServices)
  def getService(id: UUID): Future[GetServiceResponse] = serviceRegistry.ask(GetService(id, _))
  def createService(serviceCreate: ServiceCreate): Future[ServiceActionPerformed] = serviceRegistry.ask(CreateService(serviceCreate, _))
  def deleteService(id: UUID): Future[ServiceActionPerformed] = serviceRegistry.ask(DeleteService(id, _))


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("service"),summary = "Return OTP by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "OTP returned",content=Array(new Content(schema=new Schema(implementation = classOf[Service])))))
  )
  def getServiceRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getService(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.service)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("service"), summary = "Return all OTPs",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of OTPs",content = Array(new Content(schema = new Schema(implementation = classOf[Services])))))
  )
  def getServicesRoute() = get {
    metricGetCount += 1
    complete(getServices())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("service"),summary = "Delete OTP by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "OTP deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Service])))))
  )
  def deleteServiceRoute(id: String) = delete {
    onSuccess(deleteService(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("service"),summary = "Create OTP Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[ServiceCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "OTP created",content = Array(new Content(schema = new Schema(implementation = classOf[ServiceActionPerformed])))))
  )
  def createServiceRoute = post {
    entity(as[ServiceCreate]) { serviceCreate =>
      onSuccess(createService(serviceCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  override val routes: Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          getServicesRoute(),
          createServiceRoute
        )
      },
      path(Segment) { id =>
        concat(
          getServiceRoute(id),
          deleteServiceRoute(id)
        )
      }
    )
    
}
