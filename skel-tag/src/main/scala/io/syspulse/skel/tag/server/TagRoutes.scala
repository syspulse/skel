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

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, PUT, POST, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

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
import scala.util.Try

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
  val metricUpdateCount: Counter = Counter.build().name("skel_tag_update_total").help("Tag updates").register(cr)
  
  def getTags(from:Option[Int],size:Option[Int]): Future[Tags] = registry.ask(GetTags(from,size, _))
  def getTag(tags: String): Future[Try[Tag]] = registry.ask(GetTag(tags, _))
  def getSearchTag(tags: String,from:Option[Int],size:Option[Int]): Future[Tags] = registry.ask(GetSearchTag(tags,from,size, _))
  def getTypingTag(txt: String,from:Option[Int],size:Option[Int]): Future[Tags] = registry.ask(GetTypingTag(txt,from,size, _))
  
  def createTag(req: TagCreateReq): Future[Try[Tag]] = registry.ask(CreateTag(req, _))
  def updateTag(id:String,req: TagUpdateReq): Future[Try[Tag]] = registry.ask(UpdateTag(id,req, _))
  def deleteTag(id:String): Future[TagActionRes] = registry.ask(DeleteTag(id, _))
  
  def randomTag(): Future[Tag] = registry.ask(RandomTag(_))

  @GET @Path("/{tag}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tag"),summary = "Get tag by id",
    parameters = Array(new Parameter(name = "tag", in = ParameterIn.PATH, description = "Tag")),
    responses = Array(new ApiResponse(responseCode="200",description = "Object found",content=Array(new Content(schema=new Schema(implementation = classOf[Tag])))))
  )
  def getTagRoute(id: String) = get {    
    rejectEmptyResponse {
      onSuccess(getTag(id)) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/search/{tags}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tags"),summary = "Search Objects by tags", parameters = Array(
      new Parameter(name = "tags", in = ParameterIn.PATH, description = "Tags (semicolon separated)"),
      new Parameter(name = "from", in = ParameterIn.PATH, description = "Page from (inclusive)"),
      new Parameter(name = "size", in = ParameterIn.PATH, description = "Page Size"),
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Tags found",content=Array(new Content(schema=new Schema(implementation = classOf[Tags])))))
  )
  def getTagSearchRoute(tags: String) = get { 
    parameters("from".as[Int].optional,"size".as[Int].optional) { (from,size) =>
      rejectEmptyResponse {
        onSuccess(getSearchTag(tags,from,size)) { r =>
          metricGetCount.inc()
          complete(r)
        }
      }
    }
  }

  @GET @Path("/typing/{txt}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tags"),summary = "Type-ahead search", parameters = Array(
      new Parameter(name = "txt", in = ParameterIn.PATH, description = "Prefix text match"),
      new Parameter(name = "from", in = ParameterIn.PATH, description = "Page from (inclusive)"),
      new Parameter(name = "size", in = ParameterIn.PATH, description = "Page Size"),
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Tags found",content=Array(new Content(schema=new Schema(implementation = classOf[Tags])))))
  )
  def getTagTypingRoute(txt: String) = get { 
    parameters("from".as[Int].optional,"size".as[Int].optional) { (from,size) =>
      rejectEmptyResponse {
        onSuccess(getTypingTag(txt,from,size)) { r =>
          metricGetCount.inc()
          complete(r)
        }
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tag"), summary = "Return all Objects",parameters = Array(
      new Parameter(name = "from", in = ParameterIn.PATH, description = "Page from (inclusive)"),
      new Parameter(name = "size", in = ParameterIn.PATH, description = "Page Size"),
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Objects",content = Array(new Content(schema = new Schema(implementation = classOf[Tags])))))
  )
  def getTagsRoute() = get { 
    parameters("from".as[Int].optional,"size".as[Int].optional) { (from,size) =>
      onSuccess(getTags(from,size)) { r => 
        metricGetCount.inc()
        complete(r)
      }    
    }
  }

  def createTagRandomRoute() = post { 
    onSuccess(randomTag()) { r =>
      complete((StatusCodes.Created, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tag"),summary = "Create Tag",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[TagCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Tag",content = Array(new Content(schema = new Schema(implementation = classOf[Tag])))))
  )
  def createTagRoute = post {
    entity(as[TagCreateReq]) { req =>
      onSuccess(createTag(req)) { r =>
        metricCreateCount.inc()
        complete(StatusCodes.Created, r)
      }
    }
  }

  @PUT @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tag"),summary = "Update Tag",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "tag id")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[TagUpdateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "User",content = Array(new Content(schema = new Schema(implementation = classOf[Tag])))))
  )
  def updateTagRoute(id:String) = put {
    entity(as[TagUpdateReq]) { req =>
      onSuccess(updateTag(id,req)) { r =>
        metricUpdateCount.inc()
        complete(StatusCodes.OK, r)
      }
    }
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("tag"),summary = "Delete Tag",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "tag id")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "User deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Tag])))))
  )
  def deleteTagRoute(id: String) = delete {
    onSuccess(deleteTag(id)) { r =>
      metricDeleteCount.inc()      
      complete(StatusCodes.OK, r)
    }
  }

  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    authenticate()(authn =>
      concat(
        pathEndOrSingleSlash {
          concat(
            getTagsRoute() ~
            createTagRoute
          )
        },
        pathPrefix("search") {
          pathPrefix(Segment) { tags =>
            pathEndOrSingleSlash {
              getTagSearchRoute(tags)            
            }
          }
        },
        pathPrefix("typing") {
          pathPrefix(Segment) { txt =>
            pathEndOrSingleSlash {
              getTagTypingRoute(txt)            
            }
          }
        },
        pathPrefix("random") {
          createTagRandomRoute()
        },
        pathPrefix(Segment) { id =>
          pathEndOrSingleSlash {
            getTagRoute(id) ~
            updateTagRoute(id) ~
            deleteTagRoute(id)
          }
        }
      )
    )
  }
    
}
