package io.syspulse.skel.notify.server

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

import io.syspulse.skel.notify._
import io.syspulse.skel.notify.store.NotifyRegistry
import io.syspulse.skel.notify.store.NotifyRegistry._

@Path("/api/v1/notify")
class NotifyRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import NotifyJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_notify_get_total").help("Notify gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_notify_delete_total").help("Notify deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_notify_create_total").help("Notify creates").register(cr)
  
  def getNotifys(): Future[Notifys] = registry.ask(GetNotifys)
  def getNotify(id: UUID): Future[Option[Notify]] = registry.ask(GetNotify(id, _))
  def getNotifyByEid(eid: String): Future[Option[Notify]] = registry.ask(GetNotifyByEid(eid, _))

  def createNotify(notifyCreate: NotifyCreateReq): Future[Notify] = registry.ask(CreateNotify(notifyCreate, _))
  def deleteNotify(id: UUID): Future[NotifyActionRes] = registry.ask(DeleteNotify(id, _))
  def randomNotify(): Future[Notify] = registry.ask(RandomNotify(_))


  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Return Notify by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Notify id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Notify returned",content=Array(new Content(schema=new Schema(implementation = classOf[Notify])))))
  )
  def getNotifyRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getNotify(UUID.fromString(id))) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/eid/{eid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Get Notify by External Id (eid)",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "eid")),
    responses = Array(new ApiResponse(responseCode="200",description = "Notify returned",content=Array(new Content(schema=new Schema(implementation = classOf[Notify])))))
  )
  def getNotifyByEidRoute(eid: String) = get {
    rejectEmptyResponse {
      onSuccess(getNotifyByEid(eid)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"), summary = "Return all Notifys",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Notifys",content = Array(new Content(schema = new Schema(implementation = classOf[Notifys])))))
  )
  def getNotifysRoute() = get {
    metricGetCount.inc()
    complete(getNotifys())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Delete Notify by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Notify id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Notify deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Notify])))))
  )
  def deleteNotifyRoute(id: String) = delete {
    onSuccess(deleteNotify(UUID.fromString(id))) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Create Notify Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[NotifyCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Notify created",content = Array(new Content(schema = new Schema(implementation = classOf[NotifyActionRes])))))
  )
  def createNotifyRoute = post {
    entity(as[NotifyCreateReq]) { notifyCreate =>
      onSuccess(createNotify(notifyCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  def createNotifyRandomRoute() = post { 
    onSuccess(randomNotify()) { r =>
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
          //       getNotifysRoute(),
          //       createNotifyRoute
          //     )
          //   }
          // )
          concat(
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {              
                getNotifysRoute() ~                
                createNotifyRoute  
              }
            ),
            //createNotifyRoute
          )
        },
        // pathPrefix("info") {
        //   path(Segment) { notifyId => 
        //     getNotifyInfo(notifyId)
        //   }
        // },
        pathSuffix("random") {
          createNotifyRandomRoute()
        },
        pathPrefix("eid") {
          pathPrefix(Segment) { id => 
            getNotifyByEidRoute(id)
          }
        },
        pathPrefix(Segment) { id => 
          // pathPrefix("eid") {
          //   pathEndOrSingleSlash {
          //     getNotifyByEidRoute(id)
          //   } 
          //   ~
          //   path(Segment) { code =>
          //     getNotifyCodeVerifyRoute(id,code)
          //   }
          // } ~

          pathEndOrSingleSlash {
            // concat(
            //   getNotifyRoute(id),
            //   deleteNotifyRoute(id),
            // )          
            authenticate()(authn =>
              authorize(Permissions.isUser(UUID(id),authn)) {
                getNotifyRoute(id)
              } ~
              authorize(Permissions.isAdmin(authn)) {
                deleteNotifyRoute(id)
              }
            ) 
          }
        }
      )
    
}
