package io.syspulse.skel.tag.server

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

import io.syspulse.skel.auth.permissions.rbac.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.tag._
import io.syspulse.skel.tag.store.TagRegistry
import io.syspulse.skel.tag.store.TagRegistry._
import io.syspulse.skel.tag.server._

@Path("/api/v1/tag")
class TagRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import TagJson._
  import TagProto._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_tag_get_total").help("Tag gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_tag_delete_total").help("Tag deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_tag_create_total").help("Tag creates").register(cr)
  
  def getTags(): Future[Tags] = registry.ask(GetTags)
  def getTag(tags: String): Future[Tags] = registry.ask(GetTag(tags, _))
  
  def randomTag(): Future[Tag] = registry.ask(RandomTag(_))

  @GET @Path("/{tags}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tags"),summary = "Search Objects by Tags",
    parameters = Array(new Parameter(name = "tag", in = ParameterIn.PATH, description = "Tag")),
    responses = Array(new ApiResponse(responseCode="200",description = "Objects found",content=Array(new Content(schema=new Schema(implementation = classOf[Tag])))))
  )
  def getTagRoute(tags: String) = get {
    rejectEmptyResponse {
      onSuccess(getTag(tags)) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tag"), summary = "Return all Objects",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Objects",content = Array(new Content(schema = new Schema(implementation = classOf[Tags])))))
  )
  def getTagsRoute() = get {
    onSuccess(getTags()) { r => 
      metricGetCount.inc()
      complete(r)
    }    
  }

  def createTagRandomRoute() = post { 
    onSuccess(randomTag()) { r =>
      complete((StatusCodes.Created, r))
    }
  }

  override def routes: Route =
      concat(
        pathEndOrSingleSlash {
          concat(
            getTagsRoute()            
          )
        },
        pathSuffix("random") {
          createTagRandomRoute()
        },
        pathPrefix(Segment) { tags =>
          pathEndOrSingleSlash {
            getTagRoute(tags)            
          }
        }
      )
    
}
