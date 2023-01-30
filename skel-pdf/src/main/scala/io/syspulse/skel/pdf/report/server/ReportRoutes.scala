package io.syspulse.skel.pdf.report.server

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

import jakarta.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.Command

import scala.concurrent.Await
import scala.concurrent.duration.Duration

//import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.pdf.report._
import io.syspulse.skel.pdf.Config
import io.syspulse.skel.pdf.report.store.ReportRegistry._
import scala.util.Try


@Path("/api/v1/report")
class ReportRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable { 
  //with RouteAuthorizers {
  val log = Logger(s"${this}")
  
  implicit val system: ActorSystem[_] = context.system
  
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import ReportJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricDeleteCount: Counter = Counter.build().name("skel_report_delete_total").help("Report deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_report_create_total").help("Report creates").register(cr)
  
  def getReports(): Future[Reports] = registry.ask(GetReports)
  def getReport(id: UUID): Future[Try[Report]] = registry.ask(GetReport(id, _))
  def getReportByXid(xid: String): Future[Option[Report]] = registry.ask(GetReportByXid(xid, _))

  def createReport(reportCreate: ReportCreateReq): Future[ReportActionRes] = registry.ask(CreateReport(reportCreate, _))
  def deleteReport(id: UUID): Future[ReportActionRes] = registry.ask(DeleteReport(id, _))
  

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("report"),summary = "Return Report by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Report id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Report returned",content=Array(new Content(schema=new Schema(implementation = classOf[Report])))))
  )
  def getReportRoute(id: String) = get {
    rejectEmptyResponse {      
      onSuccess(getReport(UUID.fromString(id))) { r =>
        complete(r)
      }
  
    }
  }


  @GET @Path("/xid/{xid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("report"),summary = "Get Report by External Id (xid)",
    parameters = Array(new Parameter(name = "xid", in = ParameterIn.PATH, description = "xid")),
    responses = Array(new ApiResponse(responseCode="200",description = "Report returned",content=Array(new Content(schema=new Schema(implementation = classOf[Report])))))
  )
  def getReportByXidRoute(eid: String) = get {
    rejectEmptyResponse {
      onSuccess(getReportByXid(eid)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("report"), summary = "Return all Reports",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Reports",content = Array(new Content(schema = new Schema(implementation = classOf[Reports])))))
  )
  def getReportsRoute() = get {
    complete(getReports())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("report"),summary = "Delete Report by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Report id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Report deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Report])))))
  )
  def deleteReportRoute(id: String) = delete {
    onSuccess(deleteReport(UUID.fromString(id))) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("report"),summary = "Create Report Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[ReportCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Report created",content = Array(new Content(schema = new Schema(implementation = classOf[ReportActionRes])))))
  )
  def createReportRoute = post {
    entity(as[ReportCreateReq]) { reportCreate =>
      onSuccess(createReport(reportCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  override def routes: Route =
      concat(
        pathEndOrSingleSlash {
          concat(
            //authenticate()(authn =>
            //  authorize(Permissions.isAdmin(authn)) {              
                getReportsRoute() ~                
                createReportRoute  
            //  }
            //),            
          )
        },
        // pathPrefix("info") {
        //   path(Segment) { reportId => 
        //     getReportInfo(reportId)
        //   }
        // },
        
        pathPrefix("xid") {
          pathPrefix(Segment) { xid => 
            getReportByXidRoute(xid)
          }
        },
        pathPrefix(Segment) { id => 
          // pathPrefix("eid") {
          //   pathEndOrSingleSlash {
          //     getReportByEidRoute(id)
          //   } 
          //   ~
          //   path(Segment) { code =>
          //     getReportCodeVerifyRoute(id,code)
          //   }
          // } ~

          pathEndOrSingleSlash {
            //authenticate()(authn =>
            //  authorize(Permissions.isReport(UUID(id),authn)) {
                getReportRoute(id) ~
            //  } ~
            //  authorize(Permissions.isAdmin(authn)) {
                deleteReportRoute(id)
            //  }
            //) 
          }
        }
      )
    
}
