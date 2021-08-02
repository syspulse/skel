package io.syspulse.skel.shop.order

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

import io.syspulse.skel.shop.order.OrderRegistry._

@Path("/api/v1/shop/order")
class OrderRoutes(orderRegistry: ActorRef[OrderRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OrderJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("order.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("order-get-count")
  val metricPostCount: Counter = metrics.counter("order-post-count")
  val metricDeleteCount: Counter = metrics.counter("order-delete-count")
  val metricLoadCount: Counter = metrics.counter("order-load-count")
  val metricClearCount: Counter = metrics.counter("order-clear-count")


  def getOrders(): Future[Orders] = orderRegistry.ask(GetOrders)
  def getOrder(id: UUID): Future[GetOrderResponse] = orderRegistry.ask(GetOrder(id, _))
  def getOrderByItemId(oid: UUID): Future[Orders] = orderRegistry.ask(GetOrderByItemId(oid, _))
  def createOrder(orderCreate: OrderCreate): Future[OrderActionPerformed] = orderRegistry.ask(CreateOrder(orderCreate, _))
  def deleteOrder(id: UUID): Future[OrderActionPerformed] = orderRegistry.ask(DeleteOrder(id, _))
  def loadOrders(): Future[Orders] = orderRegistry.ask(LoadOrders)
  def clearOrders(): Future[ClearActionPerformed] = orderRegistry.ask(ClearOrders)
  def getRandomOrders(count:Long): Future[Orders] = orderRegistry.ask(GetRandomOrders(count,_))
  def createRandomOrders(count:Long,delay:Long): Future[Orders] = 
    orderRegistry.ask(CreateRandomOrders(count,delay,_))(timeout = Timeout(count * (delay + timeout.duration.toMillis),TimeUnit.MILLISECONDS),scheduler = system.scheduler)


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"),summary = "Return Order by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Order id (uuid) or name")),
    responses = Array(new ApiResponse(responseCode="200",description = "Order returned",content=Array(new Content(schema=new Schema(implementation = classOf[Order])))))
  )
  def getOrderRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess( getOrder(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.order)
      }
    }
  }

  @GET @Path("/order/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"),summary = "Return Order by Order Id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Order id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Order returned",content=Array(new Content(schema=new Schema(implementation = classOf[Order])))))
  )
  def getOrderByItemIdRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess( getOrderByItemId(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response) 
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"), summary = "Return all Orders",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Orders",content = Array(new Content(schema = new Schema(implementation = classOf[Orders])))))
  )
  def getOrdersRoute() = get {
    metricGetCount += 1
    complete(getOrders())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"),summary = "Delete Order by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Order id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Order deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Order])))))
  )
  def deleteOrderRoute(id: String) = delete {
    onSuccess(deleteOrder(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @DELETE @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"), summary = "Clear ALL Orders",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "All Orders deleted",content = Array(new Content(schema = new Schema(implementation = classOf[OrderActionPerformed])))))
  )
  def clearOrdersRoute() = delete {
    onSuccess(clearOrders()) { performed =>
      metricClearCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"),summary = "Create Order",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OrderCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Order created",content = Array(new Content(schema = new Schema(implementation = classOf[OrderActionPerformed])))))
  )
  def createOrderRoute = post {
    entity(as[OrderCreate]) { orderCreate =>
      onSuccess(createOrder(orderCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  @POST @Path("/load") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"), summary = "Load Orders from file",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Orders",content = Array(new Content(schema = new Schema(implementation = classOf[Orders])))))
  )
  def loadOrdersRoute() = post {
    metricLoadCount += 1
    complete(loadOrders())
  }

  @GET @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"), summary = "Return random generated Orders",
    parameters = Array(
      new Parameter(name="count",description="Number of orders to generate (def=10)",required=false),
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Orders",content = Array(new Content(schema = new Schema(implementation = classOf[Orders])))))
  )
  def getRandomOrdersRoute() = get {
    parameters("count".as[Long].withDefault(10L)) { (count) => {
      complete(getRandomOrders(count))
    }}
  }

  @POST @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("order"), summary = "Create random generated Orders",
    parameters = Array(
      new Parameter(name="count",description="Number of orders to generate (def=10)",required=false),
      new Parameter(name="delay",description="Delay in msec between inserts into Store (def=0)",required=false)
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Orders",content = Array(new Content(schema = new Schema(implementation = classOf[Orders])))))
  )
  def createRandomOrdersRoute = post {
    parameters("count".as[Long].withDefault(10L),"delay".as[Long].withDefault(0L)) { (count,delay) => {
      complete(createRandomOrders(count,delay))
    }}
  }

  override val routes: Route =
    pathPrefix("order") {
      concat(
        pathPrefix("item") {
          path(Segment) { id =>
            getOrderByItemIdRoute(id)
          }
        },
        pathPrefix("load") {           
          path("random") {
            concat(
              createRandomOrdersRoute,
              getRandomOrdersRoute()
            )
          } ~ 
          pathEndOrSingleSlash {
            loadOrdersRoute()
          }
        },
        pathEndOrSingleSlash {
          concat(
            getOrdersRoute(),
            createOrderRoute,
            clearOrdersRoute()
          )
        },
        path(Segment) { id =>
          concat(
            getOrderRoute(id),
            deleteOrderRoute(id)
          )
        }
      )
    }

}
