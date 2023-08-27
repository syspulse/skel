package io.syspulse.skel.syslog.server

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

import io.syspulse.skel.syslog._
import io.syspulse.skel.syslog.Syslog.ID
import io.syspulse.skel.syslog.store.SyslogRegistry
import io.syspulse.skel.syslog.store.SyslogRegistry._
import scala.util.Try


@Path("/")
class SyslogRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import SyslogJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_syslog_get_total").help("Syslog gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_syslog_delete_total").help("Syslog deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_syslog_create_total").help("Syslog creates").register(cr)
  
  def getSyslogs(): Future[Syslogs] = registry.ask(GetSyslogs)
  def getSyslog(id: ID): Future[Try[Syslog]] = registry.ask(GetSyslog(id, _))
  def searchSyslog(txt: String): Future[Syslogs] = registry.ask(SearchSyslog(txt, _))

  def createSyslog(syslogCreate: SyslogCreateReq): Future[Syslog] = registry.ask(CreateSyslog(syslogCreate, _))
  def deleteSyslog(id: ID): Future[SyslogActionRes] = registry.ask(DeleteSyslog(id, _))
  def randomSyslog(): Future[Syslog] = registry.ask(RandomSyslog(_))


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("syslog"),summary = "Return Syslog by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Syslog id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Syslog returned",content=Array(new Content(schema=new Schema(implementation = classOf[Syslog])))))
  )
  def getSyslogRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getSyslog(id)) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }


  @GET @Path("/search/{txt}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("syslog"),summary = "Search syslog",
    parameters = Array(new Parameter(name = "txt", in = ParameterIn.PATH, description = "text to search")),
    responses = Array(new ApiResponse(responseCode="200",description = "Syslog returned",content=Array(new Content(schema=new Schema(implementation = classOf[Syslog])))))
  )
  def searchSyslogRoute(txt: String) = get {
    rejectEmptyResponse {
      onSuccess(searchSyslog(txt)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("syslog"), summary = "Return all Syslogs",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Syslogs",content = Array(new Content(schema = new Schema(implementation = classOf[Syslogs])))))
  )
  def getSyslogsRoute() = get {
    metricGetCount.inc()
    complete(getSyslogs())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("syslog"),summary = "Delete Syslog by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Syslog id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Syslog deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Syslog])))))
  )
  def deleteSyslogRoute(id: String) = delete {
    onSuccess(deleteSyslog(id)) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("syslog"),summary = "Create Syslog Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[SyslogCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Syslog created",content = Array(new Content(schema = new Schema(implementation = classOf[SyslogActionRes])))))
  )
  def createSyslogRoute = post {
    entity(as[SyslogCreateReq]) { syslogCreate =>
      onSuccess(createSyslog(syslogCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  def createSyslogRandomRoute() = post { 
    onSuccess(randomSyslog()) { r =>
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
          //       getSyslogsRoute(),
          //       createSyslogRoute
          //     )
          //   }
          // )
          concat(
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {              
                getSyslogsRoute() ~                
                createSyslogRoute  
              }
            ),
            //createSyslogRoute
          )
        },
        // pathPrefix("info") {
        //   path(Segment) { syslogId => 
        //     getSyslogInfo(syslogId)
        //   }
        // },
        pathSuffix("random") {
          createSyslogRandomRoute()
        },
        pathPrefix("search") {
          pathPrefix(Segment) { txt => 
            searchSyslogRoute(txt)
          }
        },
        pathPrefix(Segment) { id => 
          // pathPrefix("eid") {
          //   pathEndOrSingleSlash {
          //     searchSyslogRoute(id)
          //   } 
          //   ~
          //   path(Segment) { code =>
          //     getSyslogCodeVerifyRoute(id,code)
          //   }
          // } ~

          pathEndOrSingleSlash {
            // concat(
            //   getSyslogRoute(id),
            //   deleteSyslogRoute(id),
            // )          
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {
                getSyslogRoute(id)
              } ~
              authorize(Permissions.isAdmin(authn)) {
                deleteSyslogRoute(id)
              }
            ) 
          }
        }
      )
    
}
