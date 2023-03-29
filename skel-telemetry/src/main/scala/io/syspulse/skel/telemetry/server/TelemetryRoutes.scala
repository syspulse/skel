package io.syspulse.skel.telemetry.server

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

import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings


import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes

import io.syspulse.skel.Command

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import io.syspulse.skel.auth.permissions.rbac.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.telemetry._
import io.syspulse.skel.telemetry.store.TelemetryRegistry
import io.syspulse.skel.telemetry.store.TelemetryRegistry._
import io.syspulse.skel.telemetry.server._

import io.syspulse.skel.telemetry.Telemetry.ID
import io.syspulse.skel.util.TimeUtil
import scala.util.Try

@Path(s"/")
class PriceRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends TelemetryRoutes(registry)(context)

@Path(s"/")
class TelemetryRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import TelemetryJson._
  import TelemetryProto._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_telemetry_get_total").help("Telemetry gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_telemetry_delete_total").help("Telemetry deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_telemetry_create_total").help("Telemetry creates").register(cr)
  
  def getTelemetrys(): Future[Telemetrys] = registry.ask(GetTelemetrys)
  def getTelemetry(id: ID,ts0:Long,ts1:Long): Future[Telemetrys] = registry.ask(GetTelemetry(id, ts0,ts1, _))
  def getTelemetryOp(id: ID,ts0:Long,ts1:Long,op:Option[String]): Future[Option[Telemetry]] = registry.ask(GetTelemetryOp(id, ts0,ts1, op, _))
  def getTelemetryLast(id: ID): Future[Try[Telemetry]] = registry.ask(GetTelemetryLast(id, _))
  def getTelemetryBySearch(txt: String,ts0:Long,ts1:Long): Future[Telemetrys] = registry.ask(SearchTelemetry(txt,ts0,ts1, _))

  def createTelemetry(telemetryCreate: TelemetryCreateReq): Future[Telemetry] = registry.ask(CreateTelemetry(telemetryCreate, _))
  def deleteTelemetry(id: ID): Future[TelemetryActionRes] = registry.ask(DeleteTelemetry(id, _))
  def randomTelemetry(): Future[Telemetry] = registry.ask(RandomTelemetry(_))


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"),summary = "Return telemetry by id in time range",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "telemetry id"),
      new Parameter(name = "ts0", in = ParameterIn.PATH, description = "Start Timestamp (millisec) (optional)"),
      new Parameter(name = "ts1", in = ParameterIn.PATH, description = "End Timestamp (millisec) (optional)"),
      new Parameter(name = "op", in = ParameterIn.PATH, description = "Operation (avg,last,first,sum) (optional)")
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "telemetry returned",content=Array(new Content(schema=new Schema(implementation = classOf[Telemetry])))))
  )
  def getTelemetryRoute(id: String) = get {
    rejectEmptyResponse {
      parameters("ts0".as[String].optional, "ts1".as[String].optional, "op".as[String].optional) { (ts0, ts1, op) => 
        if(! op.isDefined)
          onSuccess(getTelemetry(id,
            TimeUtil.wordToTs(ts0.getOrElse(""),0L).get,
            TimeUtil.wordToTs(ts1.getOrElse(""),Long.MaxValue).get)) { r =>
            
            metricGetCount.inc()
            complete(r)
          }
        else
          onSuccess(getTelemetryOp(id,
            TimeUtil.wordToTs(ts0.getOrElse(""),0L).get,
            TimeUtil.wordToTs(ts1.getOrElse(""),Long.MaxValue).get, op)) { r =>
            
            metricGetCount.inc()
            complete(r)
          } 
      }
    }
  }

  @GET @Path("/{id}/last") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"),summary = "Return last Telemetry by id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Telemetry id"),
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Last Telemetry returned",content=Array(new Content(schema=new Schema(implementation = classOf[Telemetry])))))
  )
  def getTelemetryLastRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getTelemetryLast(id)) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/search/{txt}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"),summary = "Search Telemetry by term",
    parameters = Array(
      new Parameter(name = "txt", in = ParameterIn.PATH, description = "search term"),
      new Parameter(name = "ts0", in = ParameterIn.PATH, description = "Start Timestamp (millisec) (optional)"),
      new Parameter(name = "ts1", in = ParameterIn.PATH, description = "End Timestamp (millisec) (optional)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Found Telemetrys",content=Array(new Content(schema=new Schema(implementation = classOf[Telemetrys])))))
  )
  def getTelemetrySearch(txt: String) = get {
    rejectEmptyResponse {
      parameters("ts0".as[String].optional, "ts1".as[String].optional) { (ts0, ts1) => 
        onSuccess(getTelemetryBySearch(txt,
          TimeUtil.wordToTs(ts0.getOrElse(""),0L).get,
          TimeUtil.wordToTs(ts1.getOrElse(""),Long.MaxValue).get)) { r =>
          
          complete(r)
        }
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"), summary = "Return all Telemetrys",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Telemetrys",content = Array(new Content(schema = new Schema(implementation = classOf[Telemetrys])))))
  )
  def getTelemetrysRoute() = get {
    metricGetCount.inc()
    complete(getTelemetrys())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"),summary = "Delete Telemetry by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Telemetry id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Telemetry deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Telemetry])))))
  )
  def deleteTelemetryRoute(id: String) = delete {
    onSuccess(deleteTelemetry(id)) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"),summary = "Create Telemetry",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[TelemetryCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Telemetry created",content = Array(new Content(schema = new Schema(implementation = classOf[TelemetryActionRes])))))
  )
  def createTelemetryRoute = post {
    entity(as[TelemetryCreateReq]) { telemetryCreate =>
      onSuccess(createTelemetry(telemetryCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  def createTelemetryRandomRoute() = post { 
    onSuccess(randomTelemetry()) { r =>
      metricCreateCount.inc()
      complete((StatusCodes.Created, r))
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
              authorize(Permissions.isAdmin(authn)) {              
                createTelemetryRoute  
              } ~
              getTelemetrysRoute()
            ),
            //getTelemetrysRoute()        
          )
        },
        // pathSuffix("random") {
        //   createTelemetryRandomRoute()
        // },
        pathPrefix("search") {
          pathPrefix(Segment) { txt => 
            authenticate()(authn => {
              getTelemetrySearch(txt)
            })
          }
        },
        pathPrefix(Segment) { id =>         
          pathSuffix("last") {
            getTelemetryLastRoute(id)
          } ~
          pathEndOrSingleSlash {
            getTelemetryRoute(id)
            authenticate()(authn =>
              authorize(Permissions.isUser(UUID(id),authn)) {
                getTelemetryRoute(id)
              } ~
              authorize(Permissions.isAdmin(authn)) {
                deleteTelemetryRoute(id)
              }
            ) 
          }
        }
      )
  }
    
}
