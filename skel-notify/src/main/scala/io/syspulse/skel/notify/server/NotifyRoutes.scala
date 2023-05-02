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

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

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

import io.syspulse.skel.notify._
import io.syspulse.skel.notify.store.NotifyRegistry
import io.syspulse.skel.notify.store.NotifyRegistry._
import scala.util.Try

@Path("/")
class NotifyRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import NotifyJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricCreateCount: Counter = Counter.build().name("skel_notify_create_total").help("Notify Ceates").register(cr)
  val metricAckCount: Counter = Counter.build().name("skel_notify_ack_total").help("Notify Acks").register(cr)
  
  def createNotify(notifyReq: NotifyReq): Future[Try[Notify]] = registry.ask(CreateNotify(notifyReq, _))
  def allNotifys(): Future[Notifys] = registry.ask(GetNotifys(_))
  def getNotify(id:UUID): Future[Try[Notify]] = registry.ask(GetNotify(id, _))
  def getNotifyUser(uid:UUID,fresh:Boolean): Future[Notifys] = registry.ask(GetNotifyUser(uid,fresh, _))
  def ackNotifyUser(uid:UUID,req:NotifyAckReq): Future[Try[Notify]] = registry.ask(AckNotifyUser(uid,req, _))
 
  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Ack Notify",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[NotifyAckReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Notify Acked",content = Array(new Content(schema = new Schema(implementation = classOf[Notify])))))
  )
  def routeAckNotifyUser(uid:String) = post {
    entity(as[NotifyAckReq]) { req =>
      onSuccess(ackNotifyUser(UUID(uid),req)) { r =>
        metricAckCount.inc()
        complete(r)
      }
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Send Notify",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[NotifyReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Notify sent",content = Array(new Content(schema = new Schema(implementation = classOf[Notify])))))
  )
  def routeNotify = post {
    entity(as[NotifyReq]) { req =>
      onSuccess(createNotify(req)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  // special route with dynamic suffix to 'TO:' destination
  //  /notify/email 
  //  /notify/stdout
  //  /notify/user
  def routeNotifyTo(via:String) = post {
    entity(as[NotifyReq]) { req =>
      onSuccess(createNotify(req.copy(to=Some(s"${via}://${req.to.getOrElse("")}")))) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  @GET @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Get All",
    responses = Array(new ApiResponse(responseCode = "200", description = "Notify sent",content = Array(new Content(schema = new Schema(implementation = classOf[Notifys])))))
  )
  def routeAll = get {
    onSuccess(allNotifys()) { r =>
      complete(r)
    }    
  }

  @GET @Path("/{id}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Get notify",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Notify id (uuid)"),      
    ),    
    responses = Array(new ApiResponse(responseCode = "200", description = "Notifys",content = Array(new Content(schema = new Schema(implementation = classOf[Notifys])))))
  )
  def routeGet(id:String) = get {
    onSuccess(getNotify(UUID(id))) { r =>
      complete(r)
    }
  }

  @GET @Path("/users/{uid}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Get User notifys",
    parameters = Array(
      new Parameter(name = "uid", in = ParameterIn.PATH, description = "User id (uuid)"),
      new Parameter(name = "fresh", in = ParameterIn.PATH, description = "Only Unacknowledged Notifies")
    ),    
    responses = Array(new ApiResponse(responseCode = "200", description = "User Notifys",content = Array(new Content(schema = new Schema(implementation = classOf[Notifys])))))
  )
  def routeGetUser(uid:String) = get {
    parameters("fresh".as[Boolean].optional) { (fresh) =>
      onSuccess(getNotifyUser(UUID(uid),fresh.getOrElse(true))) { r =>
        complete(r)
      }
    }
  }

  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    authenticate()(authn => {
      concat(
        pathEndOrSingleSlash { 
          authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
            routeAll ~
            routeNotify
          }
        },
        pathPrefix("users") {
          pathPrefix(Segment) { uid => 
            routeGetUser(uid) ~
            routeAckNotifyUser(uid)
          }          
        },
        pathPrefix(Segment) { via =>
          authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
            pathEndOrSingleSlash {
              routeNotifyTo(via) ~
              routeGet(via)      
            }
          }
        }
      )
    })
  }
    
}
