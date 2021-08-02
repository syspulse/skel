package io.syspulse.skel.shop.item

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

import io.syspulse.skel.shop.item.ItemRegistry._

@Path("/api/v1/shop/item")
class ItemRoutes(itemRegistry: ActorRef[ItemRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import ItemJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("item.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("item-get-count")
  val metricPostCount: Counter = metrics.counter("item-post-count")
  val metricDeleteCount: Counter = metrics.counter("item-delete-count")
  val metricLoadCount: Counter = metrics.counter("item-load-count")
  val metricClearCount: Counter = metrics.counter("item-clear-count")


  def getItems(): Future[Items] = itemRegistry.ask(GetItems)
  def getItem(id: UUID): Future[GetItemResponse] = itemRegistry.ask(GetItem(id, _))
  def getItemByName(name: String): Future[GetItemResponse] = itemRegistry.ask(GetItemByName(name, _))
  def createItem(itemCreate: ItemCreate): Future[ItemActionPerformed] = itemRegistry.ask(CreateItem(itemCreate, _))
  def deleteItem(id: UUID): Future[ItemActionPerformed] = itemRegistry.ask(DeleteItem(id, _))
  def loadItems(): Future[Items] = itemRegistry.ask(LoadItems)
  def clearItems(): Future[ClearActionPerformed] = itemRegistry.ask(ClearItems)
  def getRandomItems(count:Long): Future[Items] = itemRegistry.ask(GetRandomItems(count,_))
  def createRandomItems(count:Long,delay:Long): Future[Items] = 
    itemRegistry.ask(CreateRandomItems(count,delay,_))(timeout = Timeout(count * (delay + timeout.duration.toMillis),TimeUnit.MILLISECONDS),scheduler = system.scheduler)


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"),summary = "Return Item by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Item id (uuid) or name")),
    responses = Array(new ApiResponse(responseCode="200",description = "Item returned",content=Array(new Content(schema=new Schema(implementation = classOf[Item])))))
  )
  def getItemRoute(id: String) = get {
    rejectEmptyResponse {
      if(id.size == 36) 
        onSuccess( getItem(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.item)
      } else
        onSuccess( getItemByName(id)) { response =>
        metricGetCount += 1
        complete(response.item) 
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"), summary = "Return all Items",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Items",content = Array(new Content(schema = new Schema(implementation = classOf[Items])))))
  )
  def getItemsRoute() = get {
    metricGetCount += 1
    complete(getItems())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"),summary = "Delete Item by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Item id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Item deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Item])))))
  )
  def deleteItemRoute(id: String) = delete {
    onSuccess(deleteItem(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @DELETE @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"), summary = "Clear ALL Items",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "All Items deleted",content = Array(new Content(schema = new Schema(implementation = classOf[ItemActionPerformed])))))
  )
  def clearItemsRoute() = delete {
    onSuccess(clearItems()) { performed =>
      metricClearCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"),summary = "Create Item",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[ItemCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Item created",content = Array(new Content(schema = new Schema(implementation = classOf[ItemActionPerformed])))))
  )
  def createItemRoute = post {
    entity(as[ItemCreate]) { itemCreate =>
      onSuccess(createItem(itemCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  @POST @Path("/load") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"), summary = "Load Items from file",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Items",content = Array(new Content(schema = new Schema(implementation = classOf[Items])))))
  )
  def loadItemsRoute() = post {
    metricLoadCount += 1
    complete(loadItems())
  }

  @GET @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"), summary = "Return random generated Items",
    parameters = Array(
      new Parameter(name="count",description="Number of items to generate (def=10)",required=false),
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Items",content = Array(new Content(schema = new Schema(implementation = classOf[Items])))))
  )
  def getRandomItemsRoute() = get {
    parameters("count".as[Long].withDefault(10L)) { (count) => {
      complete(getRandomItems(count))
    }}
  }

  @POST @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("item"), summary = "Create random generated Items",
    parameters = Array(
      new Parameter(name="count",description="Number of items to generate (def=10)",required=false),
      new Parameter(name="delay",description="Delay in msec between inserts into Store (def=0)",required=false)
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Random Items",content = Array(new Content(schema = new Schema(implementation = classOf[Items])))))
  )
  def createRandomItemsRoute = post {
    parameters("count".as[Long].withDefault(10L),"delay".as[Long].withDefault(0L)) { (count,delay) => {
      complete(createRandomItems(count,delay))
    }}
  }

  override val routes: Route =
    pathPrefix("item") {
      concat(
        pathPrefix("load") {           
          path("random") {
            concat(
              createRandomItemsRoute,
              getRandomItemsRoute()
            )
          } ~ 
          pathEndOrSingleSlash {
            loadItemsRoute()
          }
        },
        pathEndOrSingleSlash {
          concat(
            getItemsRoute(),
            createItemRoute,
            clearItemsRoute()
          )
        },
        path(Segment) { id =>
          concat(
            getItemRoute(id),
            deleteItemRoute(id)
          )
        }
      )
    }

}
