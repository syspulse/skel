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
  val metricCreateCount: Counter = Counter.build().name("skel_notify_create_total").help("Notify creates").register(cr)
  
  def createNotify(notifyReq: NotifyReq): Future[Notify] = registry.ask(CreateNotify(notifyReq, _))
 
  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("notify"),summary = "Send Notify",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[NotifyReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Notify sent",content = Array(new Content(schema = new Schema(implementation = classOf[NotifyActionRes])))))
  )
  def notifyRoute = post {
    entity(as[NotifyReq]) { notifyReq =>
      onSuccess(createNotify(notifyReq)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  def notifyToRoute(to:String) = post {
    entity(as[NotifyReq]) { notifyReq =>
      onSuccess(createNotify(notifyReq.copy(to=Some(to)))) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  override def routes: Route =
      concat(
        pathEndOrSingleSlash { req =>
          notifyRoute(req)
        },        
        pathPrefix(Segment) { to =>
          pathEndOrSingleSlash {
            notifyToRoute(to)
            // authenticate()(authn =>
            //   authorize(Permissions.isUser(UUID(id),authn)) {
            //     getNotifyRoute(id)
            //   } ~
            //   authorize(Permissions.isAdmin(authn)) {
            //     deleteNotifyRoute(id)
            //   }
            // ) 
          }
        }
      )
    
}
