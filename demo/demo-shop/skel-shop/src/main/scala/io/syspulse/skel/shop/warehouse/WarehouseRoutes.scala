package io.syspulse.skel.shop.warehouse

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

import io.syspulse.skel.shop.warehouse.WarehouseRegistry._

@Path("/api/v1/shop/warehouse")
class WarehouseRoutes(warehouseRegistry: ActorRef[WarehouseRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WarehouseJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("warehouse.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("warehouse-get-count")
  val metricPostCount: Counter = metrics.counter("warehouse-post-count")
  val metricDeleteCount: Counter = metrics.counter("warehouse-delete-count")
  val metricLoadCount: Counter = metrics.counter("warehouse-load-count")
  val metricClearCount: Counter = metrics.counter("warehouse-clear-count")


  def getWarehouses(): Future[Warehouses] = warehouseRegistry.ask(GetWarehouses)
  def getWarehouse(id: UUID): Future[GetWarehouseResponse] = warehouseRegistry.ask(GetWarehouse(id, _))
  def getWarehouseByName(name: String): Future[GetWarehouseResponse] = warehouseRegistry.ask(GetWarehouseByName(name, _))
  def createWarehouse(warehouseCreate: WarehouseCreate): Future[WarehouseActionPerformed] = warehouseRegistry.ask(CreateWarehouse(warehouseCreate, _))
  def deleteWarehouse(id: UUID): Future[WarehouseActionPerformed] = warehouseRegistry.ask(DeleteWarehouse(id, _))
  def loadWarehouses(): Future[Warehouses] = warehouseRegistry.ask(LoadWarehouses)
  def clearWarehouses(): Future[ClearActionPerformed] = warehouseRegistry.ask(ClearWarehouses)
  def getRandomWarehouses(count:Long): Future[Warehouses] = warehouseRegistry.ask(GetRandomWarehouses(count,_))
  def createRandomWarehouses(count:Long,delay:Long): Future[Warehouses] = 
    warehouseRegistry.ask(CreateRandomWarehouses(count,delay,_))(timeout = Timeout(count * (delay + timeout.duration.toMillis),TimeUnit.MILLISECONDS),scheduler = system.scheduler)


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"),summary = "Return Warehouse by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Warehouse id (uuid) or name")),
    responses = Array(new ApiResponse(responseCode="200",description = "Warehouse returned",content=Array(new Content(schema=new Schema(implementation = classOf[Warehouse])))))
  )
  def getWarehouseRoute(id: String) = get {
    rejectEmptyResponse {
      if(id.size == 36) 
        onSuccess( getWarehouse(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.warehouse)
      } else
        onSuccess( getWarehouseByName(id)) { response =>
        metricGetCount += 1
        complete(response.warehouse) 
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"), summary = "Return all Warehouses",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Warehouses",content = Array(new Content(schema = new Schema(implementation = classOf[Warehouses])))))
  )
  def getWarehousesRoute() = get {
    metricGetCount += 1
    complete(getWarehouses())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"),summary = "Delete Warehouse by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Warehouse id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Warehouse deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Warehouse])))))
  )
  def deleteWarehouseRoute(id: String) = delete {
    onSuccess(deleteWarehouse(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @DELETE @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"), summary = "Clear ALL Warehouses",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "All Warehouses deleted",content = Array(new Content(schema = new Schema(implementation = classOf[WarehouseActionPerformed])))))
  )
  def clearWarehousesRoute() = delete {
    onSuccess(clearWarehouses()) { performed =>
      metricClearCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"),summary = "Create Warehouse",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WarehouseCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Warehouse created",content = Array(new Content(schema = new Schema(implementation = classOf[WarehouseActionPerformed])))))
  )
  def createWarehouseRoute = post {
    entity(as[WarehouseCreate]) { warehouseCreate =>
      onSuccess(createWarehouse(warehouseCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  @POST @Path("/load") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"), summary = "Load Warehouses from file",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Warehouses",content = Array(new Content(schema = new Schema(implementation = classOf[Warehouses])))))
  )
  def loadWarehousesRoute() = post {
    metricLoadCount += 1
    complete(loadWarehouses())
  }

  @GET @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"), summary = "Return random generated Warehouses",
    parameters = Array(
      new Parameter(name="count",description="Number of warehouses to generate (def=10)",required=false),
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Warehouses",content = Array(new Content(schema = new Schema(implementation = classOf[Warehouses])))))
  )
  def getRandomWarehousesRoute() = get {
    parameters("count".as[Long].withDefault(10L)) { (count) => {
      complete(getRandomWarehouses(count))
    }}
  }

  @POST @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("warehouse"), summary = "Create random generated Warehouses",
    parameters = Array(
      new Parameter(name="count",description="Number of warehouses to generate (def=10)",required=false),
      new Parameter(name="delay",description="Delay in msec between inserts into Store (def=0)",required=false)
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Warehouses",content = Array(new Content(schema = new Schema(implementation = classOf[Warehouses])))))
  )
  def createRandomWarehousesRoute = post {
    parameters("count".as[Long].withDefault(10L),"delay".as[Long].withDefault(0L)) { (count,delay) => {
      complete(createRandomWarehouses(count,delay))
    }}
  }

  override val routes: Route =
    pathPrefix("warehouse") {
      concat(
        pathPrefix("load") {           
          path("random") {
            concat(
              createRandomWarehousesRoute,
              getRandomWarehousesRoute()
            )
          } ~ 
          pathEndOrSingleSlash {
            loadWarehousesRoute()
          }
        },
        pathEndOrSingleSlash {
          concat(
            getWarehousesRoute(),
            createWarehouseRoute,
            clearWarehousesRoute()
          )
        },
        path(Segment) { id =>
          concat(
            getWarehouseRoute(id),
            deleteWarehouseRoute(id)
          )
        }
      )
    }

}
