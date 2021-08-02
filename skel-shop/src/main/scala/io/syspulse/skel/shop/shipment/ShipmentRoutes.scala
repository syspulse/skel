package io.syspulse.skel.shop.shipment

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import java.util.concurrent._

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

import io.syspulse.skel.shop.shipment.ShipmentRegistry._

@Path("/api/v1/shop/shipment")
class ShipmentRoutes(shipmentRegistry: ActorRef[ShipmentRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import ShipmentJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("shipment.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("shipment-get-count")
  val metricPostCount: Counter = metrics.counter("shipment-post-count")
  val metricDeleteCount: Counter = metrics.counter("shipment-delete-count")
  val metricLoadCount: Counter = metrics.counter("shipment-load-count")
  val metricClearCount: Counter = metrics.counter("shipment-clear-count")


  def getShipments(): Future[Shipments] = shipmentRegistry.ask(GetShipments)
  def getShipment(id: UUID): Future[GetShipmentResponse] = shipmentRegistry.ask(GetShipment(id, _))
  def getShipmentByOrderId(oid: UUID): Future[GetShipmentResponse] = shipmentRegistry.ask(GetShipmentByOrderId(oid, _))
  def createShipment(shipmentCreate: ShipmentCreate): Future[ShipmentActionPerformed] = shipmentRegistry.ask(CreateShipment(shipmentCreate, _))
  def deleteShipment(id: UUID): Future[ShipmentActionPerformed] = shipmentRegistry.ask(DeleteShipment(id, _))
  def loadShipments(): Future[Shipments] = shipmentRegistry.ask(LoadShipments)
  def clearShipments(): Future[ClearActionPerformed] = shipmentRegistry.ask(ClearShipments)
  def getRandomShipments(count:Long): Future[Shipments] = shipmentRegistry.ask(GetRandomShipments(count,_))
  def createRandomShipments(count:Long,delay:Long): Future[Shipments] = 
    shipmentRegistry.ask(CreateRandomShipments(count,delay,_))(timeout = Timeout(count * (delay + timeout.duration.toMillis),TimeUnit.MILLISECONDS),scheduler = system.scheduler)


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"),summary = "Return Shipment by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Shipment id (uuid) or name")),
    responses = Array(new ApiResponse(responseCode="200",description = "Shipment returned",content=Array(new Content(schema=new Schema(implementation = classOf[Shipment])))))
  )
  def getShipmentRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess( getShipment(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.shipment)
      }
    }
  }

  @GET @Path("/order/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"),summary = "Return Shipment by Order Id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Order id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Shipment returned",content=Array(new Content(schema=new Schema(implementation = classOf[Shipment])))))
  )
  def getShipmentByOrderIdRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess( getShipmentByOrderId(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.shipment) 
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"), summary = "Return all Shipments",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Shipments",content = Array(new Content(schema = new Schema(implementation = classOf[Shipments])))))
  )
  def getShipmentsRoute() = get {
    metricGetCount += 1
    complete(getShipments())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"),summary = "Delete Shipment by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Shipment id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Shipment deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Shipment])))))
  )
  def deleteShipmentRoute(id: String) = delete {
    onSuccess(deleteShipment(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @DELETE @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"), summary = "Clear ALL Shipments",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "All Shipments deleted",content = Array(new Content(schema = new Schema(implementation = classOf[ShipmentActionPerformed])))))
  )
  def clearShipmentsRoute() = delete {
    onSuccess(clearShipments()) { performed =>
      metricClearCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"),summary = "Create Shipment",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[ShipmentCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Shipment created",content = Array(new Content(schema = new Schema(implementation = classOf[ShipmentActionPerformed])))))
  )
  def createShipmentRoute = post {
    entity(as[ShipmentCreate]) { shipmentCreate =>
      onSuccess(createShipment(shipmentCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  @POST @Path("/load") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"), summary = "Load Shipments from file",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Shipments",content = Array(new Content(schema = new Schema(implementation = classOf[Shipments])))))
  )
  def loadShipmentsRoute() = post {
    metricLoadCount += 1
    complete(loadShipments())
  }

  @GET @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"), summary = "Return random generated Shipments",
    parameters = Array(
      new Parameter(name="count",description="Number of shipments to generate (def=10)",required=false),
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Shipments",content = Array(new Content(schema = new Schema(implementation = classOf[Shipments])))))
  )
  def getRandomShipmentsRoute() = get {
    parameters("count".as[Long].withDefault(10L)) { (count) => {
      complete(getRandomShipments(count))
    }}
  }

  @POST @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("shipment"), summary = "Create random generated Shipments",
    parameters = Array(
      new Parameter(name="count",description="Number of shipments to generate (def=10)",required=false),
      new Parameter(name="delay",description="Delay in msec between inserts into Store (def=0)",required=false)
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Shipments",content = Array(new Content(schema = new Schema(implementation = classOf[Shipments])))))
  )
  def createRandomShipmentsRoute = post {
    parameters("count".as[Long].withDefault(10L),"delay".as[Long].withDefault(0L)) { (count,delay) => {
      complete(createRandomShipments(count,delay))
    }}
  }

  override val routes: Route =
    pathPrefix("shipment") {
      concat(
        pathPrefix("order") {
          path(Segment) { id =>
            getShipmentByOrderIdRoute(id)
          }
        },
        pathPrefix("load") {           
          path("random") {
            concat(
              createRandomShipmentsRoute,
              getRandomShipmentsRoute()
            )
          } ~ 
          pathEndOrSingleSlash {
            loadShipmentsRoute()
          }
        },
        pathEndOrSingleSlash {
          concat(
            getShipmentsRoute(),
            createShipmentRoute,
            clearShipmentsRoute()
          )
        },
        path(Segment) { id =>
          concat(
            getShipmentRoute(id),
            deleteShipmentRoute(id)
          )
        }
      )
    }

}
