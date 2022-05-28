package io.syspulse.skel.world.currency

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

import io.syspulse.skel.world.currency.CurrencyRegistry._

@Path("/api/v1/world/currency")
class CurrencyRoutes(currencyRegistry: ActorRef[CurrencyRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import CurrencyJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("currency.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("currency-get-count")
  val metricPostCount: Counter = metrics.counter("currency-post-count")
  val metricDeleteCount: Counter = metrics.counter("currency-delete-count")
  val metricLoadCount: Counter = metrics.counter("currency-load-count")
  val metricClearCount: Counter = metrics.counter("currency-clear-count")


  def getCurrencys(): Future[Currencys] = currencyRegistry.ask(GetCurrencys)
  def getCurrency(id: UUID): Future[GetCurrencyResponse] = currencyRegistry.ask(GetCurrency(id, _))
  def getCurrencyByName(name: String): Future[GetCurrencyResponse] = currencyRegistry.ask(GetCurrencyByName(name, _))
  def createCurrency(currencyCreate: CurrencyCreate): Future[CurrencyActionPerformed] = currencyRegistry.ask(CreateCurrency(currencyCreate, _))
  def deleteCurrency(id: UUID): Future[CurrencyActionPerformed] = currencyRegistry.ask(DeleteCurrency(id, _))
  def loadCurrencys(): Future[Currencys] = currencyRegistry.ask(LoadCurrencys)
  def clearCurrencys(): Future[ClearActionPerformed] = currencyRegistry.ask(ClearCurrencys)


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("currency"),summary = "Return Currency by id/name",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Currency id (uuid) or name (short/long)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Currency returned",content=Array(new Content(schema=new Schema(implementation = classOf[Currency])))))
  )
  def getCurrencyRoute(id: String) = get {
    rejectEmptyResponse {
      if(id.size == 36) 
        onSuccess( getCurrency(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.currency)
      } else
        onSuccess( getCurrencyByName(id)) { response =>
        metricGetCount += 1
        complete(response.currency) 
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("currency"), summary = "Return all Currencys",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Currencys",content = Array(new Content(schema = new Schema(implementation = classOf[Currencys])))))
  )
  def getCurrencysRoute() = get {
    metricGetCount += 1
    complete(getCurrencys())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("currency"),summary = "Delete Currency by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Currency id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Currency deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Currency])))))
  )
  def deleteCurrencyRoute(id: String) = delete {
    onSuccess(deleteCurrency(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @DELETE @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("currency"), summary = "Clear ALL Currencys",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "All Currencys deleted",content = Array(new Content(schema = new Schema(implementation = classOf[CurrencyActionPerformed])))))
  )
  def clearCurrencysRoute() = delete {
    onSuccess(clearCurrencys()) { performed =>
      metricClearCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("currency"),summary = "Create Currency",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[CurrencyCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Currency created",content = Array(new Content(schema = new Schema(implementation = classOf[CurrencyActionPerformed])))))
  )
  def createCurrencyRoute = post {
    entity(as[CurrencyCreate]) { currencyCreate =>
      onSuccess(createCurrency(currencyCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  @POST @Path("/load") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("currency"), summary = "Load Currencys from file",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Currencys",content = Array(new Content(schema = new Schema(implementation = classOf[Currencys])))))
  )
  def loadCurrencysRoute() = post {
    metricLoadCount += 1
    complete(loadCurrencys())
  }

  override val routes: Route =
    pathPrefix("currency") {
      concat(
        path("load") { 
          loadCurrencysRoute()
        },
        pathEndOrSingleSlash {
          concat(
            getCurrencysRoute(),
            createCurrencyRoute,
            clearCurrencysRoute()
          )
        },
        path(Segment) { id =>
          concat(
            getCurrencyRoute(id),
            deleteCurrencyRoute(id)
          )
        }
      )
    }

}
