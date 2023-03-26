package io.syspulse.skel.lake.job.server

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
import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
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

import io.syspulse.skel.lake.job._
import io.syspulse.skel.lake.job.store._

// @Path("/")
// class JobRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
//   //val log = Logger(s"${this}")
//   implicit val system: ActorSystem[_] = context.system
  
//   implicit val permissions = Permissions()

//   import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
//   import JobJson._
  
//   // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
//   val cr = new CollectorRegistry(true);
//   val metricCreateCount: Counter = Counter.build().name("skel_job_create_total").help("Job creates").register(cr)
  
//   def createJob(jobReq: JobReq): Future[Job] = registry.ask(CreateJob(jobReq, _))
 
//   @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
//   @Produces(Array(MediaType.APPLICATION_JSON))
//   @Operation(tags = Array("job"),summary = "Send Job",
//     requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[JobReq])))),
//     responses = Array(new ApiResponse(responseCode = "200", description = "Job sent",content = Array(new Content(schema = new Schema(implementation = classOf[JobActionRes])))))
//   )
//   def jobRoute = post {
//     entity(as[JobReq]) { jobReq =>
//       onSuccess(createJob(jobReq)) { r =>
//         metricCreateCount.inc()
//         complete((StatusCodes.Created, r))
//       }
//     }
//   }

//   def jobToRoute(via:String) = post {
//     entity(as[JobReq]) { jobReq =>
//       onSuccess(createJob(jobReq.copy(to=Some(s"${via}://${jobReq.to.getOrElse("")}")))) { r =>
//         metricCreateCount.inc()
//         complete((StatusCodes.Created, r))
//       }
//     }
//   }

//   val corsAllow = CorsSettings(system.classicSystem)
//     //.withAllowGenericHttpRequests(true)
//     .withAllowCredentials(true)
//     .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

//   override def routes: Route = cors(corsAllow) {
//     authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
//       concat(
//         pathEndOrSingleSlash { req =>
//           jobRoute(req)
//         },        
//         pathPrefix(Segment) { via =>
//           pathEndOrSingleSlash {
//             jobToRoute(via)            
//           }
//         }
//       )
//     })
//   }
    
// }
