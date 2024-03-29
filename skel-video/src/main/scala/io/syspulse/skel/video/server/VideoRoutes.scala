package io.syspulse.skel.video.server

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
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes

import io.syspulse.skel.Command

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.video._
import io.syspulse.skel.video.store.VideoRegistry
import io.syspulse.skel.video.store.VideoRegistry._
import io.syspulse.skel.video.server._
import scala.util.Try

@Path("/")
class VideoRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import VideoJson._
  import VideoProto._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_video_get_total").help("Video gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_video_delete_total").help("Video deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_video_create_total").help("Video creates").register(cr)
  
  def getVideos(): Future[Videos] = registry.ask(GetVideos)
  def getVideo(id: VID): Future[Try[Video]] = registry.ask(GetVideo(id, _))
  def getVideoBySearch(txt: String): Future[Videos] = registry.ask(SearchVideo(txt, _))
  def getVideoByTyping(txt: String): Future[Videos] = registry.ask(TypingVideo(txt, _))

  def createVideo(videoCreate: VideoCreateReq): Future[Video] = registry.ask(CreateVideo(videoCreate, _))
  def deleteVideo(id: VID): Future[VideoActionRes] = registry.ask(DeleteVideo(id, _))
  def randomVideo(): Future[Video] = registry.ask(RandomVideo(_))


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("video"),summary = "Return Video by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Video id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Video returned",content=Array(new Content(schema=new Schema(implementation = classOf[Video])))))
  )
  def getVideoRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getVideo(VID.fromString(id))) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/search/{txt}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("video"),summary = "Search Video by term",
    parameters = Array(new Parameter(name = "txt", in = ParameterIn.PATH, description = "search term")),
    responses = Array(new ApiResponse(responseCode="200",description = "Found Videos",content=Array(new Content(schema=new Schema(implementation = classOf[Videos])))))
  )
  def getVideoSearch(txt: String) = get {
    rejectEmptyResponse {
      onSuccess(getVideoBySearch(txt)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/typing/{txt}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("video"),summary = "Search Video by Type-Ahead",
    parameters = Array(new Parameter(name = "txt", in = ParameterIn.PATH, description = "search letters")),
    responses = Array(new ApiResponse(responseCode="200",description = "Found Videos",content=Array(new Content(schema=new Schema(implementation = classOf[Videos])))))
  )
  def getVideoTyping(txt: String) = get {
    rejectEmptyResponse {
      onSuccess(getVideoByTyping(txt)) { r =>
        complete(r)
      }
    }
  }



  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("video"), summary = "Return all Videos",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Videos",content = Array(new Content(schema = new Schema(implementation = classOf[Videos])))))
  )
  def getVideosRoute() = get {
    metricGetCount.inc()
    complete(getVideos())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("video"),summary = "Delete Video by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Video id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Video deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Video])))))
  )
  def deleteVideoRoute(id: String) = delete {
    onSuccess(deleteVideo(VID.fromString(id))) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("video"),summary = "Create Video",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[VideoCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Video created",content = Array(new Content(schema = new Schema(implementation = classOf[VideoActionRes])))))
  )
  def createVideoRoute = post {
    entity(as[VideoCreateReq]) { videoCreate =>
      onSuccess(createVideo(videoCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  def createVideoRandomRoute() = post { 
    onSuccess(randomVideo()) { r =>
      metricCreateCount.inc()
      complete((StatusCodes.Created, r))
    }
  }

  override def routes: Route =
      concat(
        pathEndOrSingleSlash {
          concat(
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {              
                createVideoRoute  
              } ~
              getVideosRoute()
            ),          
          )
        },
        pathSuffix("random") {
          createVideoRandomRoute()
        },
        pathPrefix("search") {
          pathPrefix(Segment) { txt => 
            getVideoSearch(txt)
          }
        },
        pathPrefix("typing") {
          pathPrefix(Segment) { txt => 
            getVideoTyping(txt)
          }
        },
        pathPrefix(Segment) { id =>         
          pathEndOrSingleSlash {
            authenticate()(authn =>
              //authorize(Permissions.isUser(UUID(id),authn)) {
                getVideoRoute(id)
              //} ~
              ~ 
              authorize(Permissions.isAdmin(authn)) {
                deleteVideoRoute(id)
              }
            ) 
          }
        }
      )
    
}
