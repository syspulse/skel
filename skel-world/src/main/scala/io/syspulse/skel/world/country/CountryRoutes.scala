package io.syspulse.skel.world.country

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

import io.syspulse.skel.world.country.CountryRegistry._

@Path("/api/v1/world/country")
class CountryRoutes(countryRegistry: ActorRef[CountryRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import CountryJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("country.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("country-get-count")
  val metricPostCount: Counter = metrics.counter("country-post-count")
  val metricDeleteCount: Counter = metrics.counter("country-delete-count")
  val metricLoadCount: Counter = metrics.counter("country-load-count")
  val metricClearCount: Counter = metrics.counter("country-clear-count")


  def getCountrys(): Future[Countrys] = countryRegistry.ask(GetCountrys)
  def getCountry(id: UUID): Future[GetCountryResponse] = countryRegistry.ask(GetCountry(id, _))
  def getCountryByName(name: String): Future[GetCountryResponse] = countryRegistry.ask(GetCountryByName(name, _))
  def createCountry(countryCreate: CountryCreate): Future[CountryActionPerformed] = countryRegistry.ask(CreateCountry(countryCreate, _))
  def deleteCountry(id: UUID): Future[CountryActionPerformed] = countryRegistry.ask(DeleteCountry(id, _))
  def loadCountrys(): Future[Countrys] = countryRegistry.ask(LoadCountrys)
  def clearCountrys(): Future[ClearActionPerformed] = countryRegistry.ask(ClearCountrys)


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("country"),summary = "Return Country by id/name",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Country id (uuid) or name (short/long)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Country returned",content=Array(new Content(schema=new Schema(implementation = classOf[Country])))))
  )
  def getCountryRoute(id: String) = get {
    rejectEmptyResponse {
      if(id.size == 36) 
        onSuccess( getCountry(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.country)
      } else
        onSuccess( getCountryByName(id)) { response =>
        metricGetCount += 1
        complete(response.country) 
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("country"), summary = "Return all Countrys",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Countrys",content = Array(new Content(schema = new Schema(implementation = classOf[Countrys])))))
  )
  def getCountrysRoute() = get {
    metricGetCount += 1
    complete(getCountrys())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("country"),summary = "Delete Country by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Country id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Country deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Country])))))
  )
  def deleteCountryRoute(id: String) = delete {
    onSuccess(deleteCountry(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @DELETE @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("country"), summary = "Clear ALL Countrys",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "All Countrys deleted",content = Array(new Content(schema = new Schema(implementation = classOf[CountryActionPerformed])))))
  )
  def clearCountrysRoute() = delete {
    onSuccess(clearCountrys()) { performed =>
      metricClearCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("country"),summary = "Create Country",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[CountryCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Country created",content = Array(new Content(schema = new Schema(implementation = classOf[CountryActionPerformed])))))
  )
  def createCountryRoute = post {
    entity(as[CountryCreate]) { countryCreate =>
      onSuccess(createCountry(countryCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  @POST @Path("/load") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("country"), summary = "Load Countrys from file",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Countrys",content = Array(new Content(schema = new Schema(implementation = classOf[Countrys])))))
  )
  def loadCountrysRoute() = post {
    metricLoadCount += 1
    complete(loadCountrys())
  }

  override val routes: Route =
    pathPrefix("country") {
      concat(
        path("load") { 
          loadCountrysRoute()
        },
        pathEndOrSingleSlash {
          concat(
            getCountrysRoute(),
            createCountryRoute,
            clearCountrysRoute()
          )
        },
        path(Segment) { id =>
          concat(
            getCountryRoute(id),
            deleteCountryRoute(id)
          )
        }
      )
    }

}
