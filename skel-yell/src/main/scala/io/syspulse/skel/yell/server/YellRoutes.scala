package io.syspulse.skel.yell.server

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

import io.syspulse.skel.auth.permissions.rbac.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.yell._
import io.syspulse.skel.yell.Yell.ID
import io.syspulse.skel.yell.store.YellRegistry
import io.syspulse.skel.yell.store.YellRegistry._


@Path("/")
class YellRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import YellJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_yell_get_total").help("Yell gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_yell_delete_total").help("Yell deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_yell_create_total").help("Yell creates").register(cr)
  
  def getYells(): Future[Yells] = registry.ask(GetYells)
  def getYell(id: ID): Future[Option[Yell]] = registry.ask(GetYell(id, _))
  def searchYell(txt: String): Future[List[Yell]] = registry.ask(SearchYell(txt, _))

  def createYell(yellCreate: YellCreateReq): Future[Yell] = registry.ask(CreateYell(yellCreate, _))
  def deleteYell(id: ID): Future[YellActionRes] = registry.ask(DeleteYell(id, _))
  def randomYell(): Future[Yell] = registry.ask(RandomYell(_))


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("yell"),summary = "Return Yell by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Yell id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Yell returned",content=Array(new Content(schema=new Schema(implementation = classOf[Yell])))))
  )
  def getYellRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getYell(id)) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }


  @GET @Path("/search/{txt}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("yell"),summary = "Search yell",
    parameters = Array(new Parameter(name = "txt", in = ParameterIn.PATH, description = "text to search")),
    responses = Array(new ApiResponse(responseCode="200",description = "Yell returned",content=Array(new Content(schema=new Schema(implementation = classOf[Yell])))))
  )
  def searchYellRoute(txt: String) = get {
    rejectEmptyResponse {
      onSuccess(searchYell(txt)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("yell"), summary = "Return all Yells",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Yells",content = Array(new Content(schema = new Schema(implementation = classOf[Yells])))))
  )
  def getYellsRoute() = get {
    metricGetCount.inc()
    complete(getYells())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("yell"),summary = "Delete Yell by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Yell id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Yell deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Yell])))))
  )
  def deleteYellRoute(id: String) = delete {
    onSuccess(deleteYell(id)) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("yell"),summary = "Create Yell Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[YellCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Yell created",content = Array(new Content(schema = new Schema(implementation = classOf[YellActionRes])))))
  )
  def createYellRoute = post {
    entity(as[YellCreateReq]) { yellCreate =>
      onSuccess(createYell(yellCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  def createYellRandomRoute() = post { 
    onSuccess(randomYell()) { r =>
      metricCreateCount.inc()
      complete((StatusCodes.Created, r))
    }
  }

  override def routes: Route =
      concat(
        pathEndOrSingleSlash {
          // authenticate()(authn =>
          //   authorize(Permissions.isAdmin(authn)) {
          //     concat(
          //       getYellsRoute(),
          //       createYellRoute
          //     )
          //   }
          // )
          concat(
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {              
                getYellsRoute() ~                
                createYellRoute  
              }
            ),
            //createYellRoute
          )
        },
        // pathPrefix("info") {
        //   path(Segment) { yellId => 
        //     getYellInfo(yellId)
        //   }
        // },
        pathSuffix("random") {
          createYellRandomRoute()
        },
        pathPrefix("search") {
          pathPrefix(Segment) { txt => 
            searchYellRoute(txt)
          }
        },
        pathPrefix(Segment) { id => 
          // pathPrefix("eid") {
          //   pathEndOrSingleSlash {
          //     searchYellRoute(id)
          //   } 
          //   ~
          //   path(Segment) { code =>
          //     getYellCodeVerifyRoute(id,code)
          //   }
          // } ~

          pathEndOrSingleSlash {
            // concat(
            //   getYellRoute(id),
            //   deleteYellRoute(id),
            // )          
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {
                getYellRoute(id)
              } ~
              authorize(Permissions.isAdmin(authn)) {
                deleteYellRoute(id)
              }
            ) 
          }
        }
      )
    
}
