package io.syspulse.skel.wf.ext.server

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling.ToResponseMarshaller

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.Command

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}
import io.syspulse.skel.wf.ext.store.WorkflowRegistry
import io.syspulse.skel.wf.ext.store.WorkflowRegistry._

/**
 * Workflow `ext` REST API:
 *   /api/v1/wf/ext/schema  - WorkflowSchema CRUD (+ ?detector={id|full}, + /dsl)
 *   /api/v1/wf/ext/config  - WorkflowConfig CRUD (+ ?detector={id|full}, + /dsl, /xid, /oid)
 *   /api/v1/wf/ext/graf    - WorkflowGraf CRUD (visual configuration)
 */
@Path("/")
class WorkflowRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable {

  implicit val system: ActorSystem[_] = context.system

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowGrafJson._
  import io.hacken.ext.detector.DetectorSchemaJson._
  import io.hacken.ext.detector.DetectorConfigJson._

  // ---- schema asks ----
  def getSchemas(from: Option[Long], size: Option[Long], detail: Boolean): Future[Try[WorkflowSchemas]] = registry.ask(GetSchemas(from, size, detail, _))
  def getSchema(id: Int, detail: Boolean): Future[Try[WorkflowSchemaView]] = registry.ask(GetSchema(id, detail, _))
  def createSchema(req: WorkflowSchemaCreateReq): Future[Try[WorkflowSchema]] = registry.ask(CreateSchema(req, _))
  def createSchemaDsl(req: WorkflowSchemaDslReq): Future[Try[WorkflowSchema]] = registry.ask(CreateSchemaDsl(req, _))
  def updateSchema(id: Int, req: WorkflowSchemaUpdateReq): Future[Try[WorkflowSchema]] = registry.ask(UpdateSchema(id, req, _))
  def deleteSchema(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteSchema(id, _))

  // ---- config asks ----
  def getConfigs(from: Option[Long], size: Option[Long], detail: Boolean): Future[Try[WorkflowConfigs]] = registry.ask(GetConfigs(from, size, detail, _))
  def getConfig(id: Int, detail: Boolean): Future[Try[WorkflowConfigView]] = registry.ask(GetConfig(id, detail, _))
  def getConfigByXid(xid: String): Future[Option[WorkflowConfig]] = registry.ask(GetConfigByXid(xid, _))
  def getConfigsByOid(oid: String): Future[Try[WorkflowConfigs]] = registry.ask(GetConfigsByOid(oid, _))
  def createConfig(req: WorkflowConfigCreateReq): Future[Try[WorkflowConfig]] = registry.ask(CreateConfig(req, _))
  def createConfigDsl(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(CreateConfigDsl(req, _))
  def updateConfig(id: Int, req: WorkflowConfigUpdateReq): Future[Try[WorkflowConfig]] = registry.ask(UpdateConfig(id, req, _))
  def deleteConfig(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteConfig(id, _))

  // ---- graf asks ----
  def getGrafs(from: Option[Long], size: Option[Long]): Future[Try[WorkflowGrafs]] = registry.ask(GetGrafs(from, size, _))
  def getGraf(id: Int): Future[Try[WorkflowGraf]] = registry.ask(GetGraf(id, _))
  def createGraf(req: WorkflowGrafCreateReq): Future[Try[WorkflowGraf]] = registry.ask(CreateGraf(req, _))
  def deleteGraf(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteGraf(id, _))

  // ---- detector-schema asks ----
  def getDetectorSchemas(from: Option[Long], size: Option[Long]): Future[Try[DetectorSchemas]] = registry.ask(GetDetectorSchemas(from, size, _))
  def getDetectorSchema(id: Int): Future[Try[DetectorSchema]] = registry.ask(GetDetectorSchema(id, _))
  def createDetectorSchema(req: DetectorSchemaCreateReq): Future[Try[DetectorSchema]] = registry.ask(CreateDetectorSchema(req, _))
  def updateDetectorSchema(id: Int, req: DetectorSchemaUpdateReq): Future[Try[DetectorSchema]] = registry.ask(UpdateDetectorSchema(id, req, _))
  def deleteDetectorSchema(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteDetectorSchema(id, _))

  // ---- detector-config asks ----
  def getDetectorConfigs(from: Option[Long], size: Option[Long]): Future[Try[DetectorConfigs]] = registry.ask(GetDetectorConfigs(from, size, _))
  def getDetectorConfig(id: Int): Future[Try[DetectorConfig]] = registry.ask(GetDetectorConfig(id, _))
  def createDetectorConfig(req: DetectorConfigCreateReq): Future[Try[DetectorConfig]] = registry.ask(CreateDetectorConfig(req, _))
  def updateDetectorConfig(id: Int, req: DetectorConfigUpdateReq): Future[Try[DetectorConfig]] = registry.ask(UpdateDetectorConfig(id, req, _))
  def deleteDetectorConfig(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteDetectorConfig(id, _))

  private def isFull(detector: Option[String]): Boolean = detector.exists(_.equalsIgnoreCase("full"))

  /** Complete a `Future[Try[T]]`: Success -> 200 body, Failure -> 404 (not found). */
  private def completeTry[T](f: Future[Try[T]])(implicit m: ToResponseMarshaller[T]): Route =
    onComplete(f) {
      case Success(Success(v)) => complete(v)
      case Success(Failure(e)) => complete(StatusCodes.NotFound -> s"not found: ${e.getMessage}")
      case Failure(e)          => complete(StatusCodes.InternalServerError -> s"${e.getMessage}")
    }

  // ================================================================ schema routes
  @GET @Path("/schema") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "List WorkflowSchemas",
    parameters = Array(
      new Parameter(name = "from", in = ParameterIn.QUERY, description = "Page offset"),
      new Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size"),
      new Parameter(name = "detector", in = ParameterIn.QUERY, description = "id|full")),
    responses = Array(new ApiResponse(responseCode = "200", description = "schemas",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemas]))))))
  def getSchemasRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?, "detector".?) { (from, size, detector) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => completeTry(getSchemas(from, size, isFull(detector)))
      }
    }
  }

  @GET @Path("/schema/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "Get WorkflowSchema by id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "schema id"),
      new Parameter(name = "detector", in = ParameterIn.QUERY, description = "id|full")),
    responses = Array(new ApiResponse(responseCode = "200", description = "schema",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemaView]))))))
  def getSchemaRoute(id: Int) = get {
    parameter("detector".?) { detector =>
      completeTry(getSchema(id, isFull(detector)))
    }
  }

  @POST @Path("/schema") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "Create WorkflowSchema",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemaCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchema]))))))
  def createSchemaRoute() = post {
    entity(as[WorkflowSchemaCreateReq]) { req => completeTry(createSchema(req)) }
  }

  def createSchemaDslRoute() = post {
    entity(as[WorkflowSchemaDslReq]) { req => completeTry(createSchemaDsl(req)) }
  }

  def updateSchemaRoute(id: Int) = put {
    entity(as[WorkflowSchemaUpdateReq]) { req => completeTry(updateSchema(id, req)) }
  }

  def deleteSchemaRoute(id: Int) = delete { complete(deleteSchema(id)) }

  // ================================================================ config routes
  @GET @Path("/config") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "List WorkflowConfigs",
    parameters = Array(
      new Parameter(name = "from", in = ParameterIn.QUERY, description = "Page offset"),
      new Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size"),
      new Parameter(name = "detector", in = ParameterIn.QUERY, description = "id|full")),
    responses = Array(new ApiResponse(responseCode = "200", description = "configs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigs]))))))
  def getConfigsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?, "detector".?) { (from, size, detector) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => completeTry(getConfigs(from, size, isFull(detector)))
      }
    }
  }

  @GET @Path("/config/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Get WorkflowConfig by id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "config id"),
      new Parameter(name = "detector", in = ParameterIn.QUERY, description = "id|full")),
    responses = Array(new ApiResponse(responseCode = "200", description = "config",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigView]))))))
  def getConfigRoute(id: Int) = get {
    parameter("detector".?) { detector =>
      completeTry(getConfig(id, isFull(detector)))
    }
  }

  def getConfigByXidRoute(xid: String) = get { rejectEmptyResponse { complete(getConfigByXid(xid)) } }
  def getConfigsByOidRoute(oid: String) = get { completeTry(getConfigsByOid(oid)) }

  @POST @Path("/config") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Create WorkflowConfig from WorkflowSchema",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createConfigRoute() = post {
    entity(as[WorkflowConfigCreateReq]) { req => completeTry(createConfig(req)) }
  }

  def createConfigDslRoute() = post {
    entity(as[WorkflowConfigDslReq]) { req => completeTry(createConfigDsl(req)) }
  }

  def updateConfigRoute(id: Int) = put {
    entity(as[WorkflowConfigUpdateReq]) { req => completeTry(updateConfig(id, req)) }
  }

  def deleteConfigRoute(id: Int) = delete { complete(deleteConfig(id)) }

  // ================================================================ graf routes
  @GET @Path("/graf") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "List WorkflowGrafs",
    responses = Array(new ApiResponse(responseCode = "200", description = "grafs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGrafs]))))))
  def getGrafsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => completeTry(getGrafs(from, size))
      }
    }
  }

  @GET @Path("/graf/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "Get WorkflowGraf by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "graf id")),
    responses = Array(new ApiResponse(responseCode = "200", description = "graf",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGraf]))))))
  def getGrafRoute(id: Int) = get { completeTry(getGraf(id)) }

  @POST @Path("/graf") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "Create WorkflowGraf",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGrafCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGraf]))))))
  def createGrafRoute() = post {
    entity(as[WorkflowGrafCreateReq]) { req => completeTry(createGraf(req)) }
  }

  def deleteGrafRoute(id: Int) = delete { complete(deleteGraf(id)) }

  // ================================================================ detector-schema routes
  def getDetectorSchemasRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => completeTry(getDetectorSchemas(from, size))
      }
    }
  }
  def getDetectorSchemaRoute(id: Int) = get { completeTry(getDetectorSchema(id)) }
  def createDetectorSchemaRoute() = post {
    entity(as[DetectorSchemaCreateReq]) { req => completeTry(createDetectorSchema(req)) }
  }
  def updateDetectorSchemaRoute(id: Int) = put {
    entity(as[DetectorSchemaUpdateReq]) { req => completeTry(updateDetectorSchema(id, req)) }
  }
  def deleteDetectorSchemaRoute(id: Int) = delete { complete(deleteDetectorSchema(id)) }

  // ================================================================ detector-config routes
  def getDetectorConfigsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => completeTry(getDetectorConfigs(from, size))
      }
    }
  }
  def getDetectorConfigRoute(id: Int) = get { completeTry(getDetectorConfig(id)) }
  def createDetectorConfigRoute() = post {
    entity(as[DetectorConfigCreateReq]) { req => completeTry(createDetectorConfig(req)) }
  }
  def updateDetectorConfigRoute(id: Int) = put {
    entity(as[DetectorConfigUpdateReq]) { req => completeTry(updateDetectorConfig(id, req)) }
  }
  def deleteDetectorConfigRoute(id: Int) = delete { complete(deleteDetectorConfig(id)) }

  val corsAllow = CorsSettings(system.classicSystem)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS, HttpMethods.GET, HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE, HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    concat(
      pathPrefix("schema") {
        concat(
          pathPrefix("dsl") { pathEndOrSingleSlash { createSchemaDslRoute() } },
          pathPrefix(IntNumber) { id =>
            pathEndOrSingleSlash {
              getSchemaRoute(id) ~ updateSchemaRoute(id) ~ deleteSchemaRoute(id)
            }
          },
          pathEndOrSingleSlash { getSchemasRoute() ~ createSchemaRoute() },
        )
      },
      pathPrefix("config") {
        concat(
          pathPrefix("dsl") { pathEndOrSingleSlash { createConfigDslRoute() } },
          pathPrefix("xid") { pathPrefix(Segment) { xid => getConfigByXidRoute(xid) } },
          pathPrefix("oid") { pathPrefix(Segment) { oid => getConfigsByOidRoute(oid) } },
          pathPrefix(IntNumber) { id =>
            pathEndOrSingleSlash {
              getConfigRoute(id) ~ updateConfigRoute(id) ~ deleteConfigRoute(id)
            }
          },
          pathEndOrSingleSlash { getConfigsRoute() ~ createConfigRoute() },
        )
      },
      pathPrefix("graf") {
        concat(
          pathPrefix(IntNumber) { id =>
            pathEndOrSingleSlash { getGrafRoute(id) ~ deleteGrafRoute(id) }
          },
          pathEndOrSingleSlash { getGrafsRoute() ~ createGrafRoute() },
        )
      },
      pathPrefix("detector") {
        concat(
          pathPrefix("schema") {
            concat(
              pathPrefix(IntNumber) { id =>
                pathEndOrSingleSlash { getDetectorSchemaRoute(id) ~ updateDetectorSchemaRoute(id) ~ deleteDetectorSchemaRoute(id) }
              },
              pathEndOrSingleSlash { getDetectorSchemasRoute() ~ createDetectorSchemaRoute() },
            )
          },
          pathPrefix("config") {
            concat(
              pathPrefix(IntNumber) { id =>
                pathEndOrSingleSlash { getDetectorConfigRoute(id) ~ updateDetectorConfigRoute(id) ~ deleteDetectorConfigRoute(id) }
              },
              pathEndOrSingleSlash { getDetectorConfigsRoute() ~ createDetectorConfigRoute() },
            )
          },
        )
      },
    )
  }
}
