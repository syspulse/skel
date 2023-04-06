package io.syspulse.skel.lake.job.server

import scala.util.Try

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
import io.syspulse.skel.lake.job.server.JobJson
import io.syspulse.skel.lake.job.server._

import JobRegistry._

@Path("/")
class JobRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  //val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JobJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricCreateCount: Counter = Counter.build().name("skel_job_create_total").help("Job creates").register(cr)
  
  def createJob(uid:Option[UUID],req: JobCreateReq): Future[Try[Job]] = registry.ask(CreateJob(uid,req, _))
  def getJobs(): Future[Jobs] = registry.ask(GetJobs)
  def getJob(uid:Option[UUID],id: Job.ID): Future[Try[Job]] = registry.ask(GetJob(uid,id, _))  
  def deleteJob(uid:Option[UUID],id: Job.ID): Future[JobRes] = registry.ask(DeleteJob(uid,id, _))

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("job"),summary = "Return Job ",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Job ID")),
    responses = Array(new ApiResponse(responseCode="200",description = "Job",content=Array(new Content(schema=new Schema(implementation = classOf[Job])))))
  )
  def getJobRoute(uid:Option[UUID],id:String) = get {
    rejectEmptyResponse {
      onSuccess(getJob(uid,id)) { r =>
        complete(r)
      }
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("job"),summary = "Create Job",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[JobCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Job sent",content = Array(new Content(schema = new Schema(implementation = classOf[Job])))))
  )
  def createJobRoute(uid:Option[UUID]) = post {
    entity(as[JobCreateReq]) { req =>
      onSuccess(createJob(uid,req)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("job"), summary = "Return all Jobs",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Jobs",content = Array(new Content(schema = new Schema(implementation = classOf[Jobs])))))
  )
  def getJobsRoute() = get {
    complete(getJobs())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("user"),summary = "Delete User by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Job ID")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Job deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Job])))))
  )
  def deleteJobRoute(uid:Option[UUID],id: String) = delete {
    onSuccess(deleteJob(uid,id)) { r =>
      complete(StatusCodes.OK, r)
    }
  }

  
  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
      concat(
        pathEndOrSingleSlash {
          concat(
            authenticate()(authn =>
              //authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) 
              {
                getJobsRoute() ~                
                createJobRoute(authn.getUser)
              }
            ),            
          )
        },
        pathPrefix(Segment) { id => 
          pathEndOrSingleSlash {
            authenticate()(authn =>
              getJobRoute(authn.getUser,id) ~
              deleteJobRoute(authn.getUser,id)              
            ) 
          }
        }
      )
  }
    
}
