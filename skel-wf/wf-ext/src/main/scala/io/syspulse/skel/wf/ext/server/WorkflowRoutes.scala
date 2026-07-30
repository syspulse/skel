package io.syspulse.skel.wf.ext.server

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._

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
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineWorkflows, TrackMapper}

/**
 * Workflow `ext` REST API:
 *   /api/v1/wf/ext/schema  - WorkflowSchema CRUD (+ ?entity={graf,detector,schema|all}, + /dsl)
 *   /api/v1/wf/ext/config  - WorkflowConfig CRUD (+ ?entity={graf,detector,schema|all}, + /dsl, /xid, /oid)
 *   /api/v1/wf/ext/graf    - WorkflowGraf CRUD (visual configuration)
 *   /api/v1/wf/ext/engine  - Engine runtime state (Temporal), enabled when an Engine is configured
 */
@Path("/")
class WorkflowRoutes(registry: ActorRef[Command], engine: Option[Engine] = None)(implicit context: ActorContext[_]) extends CommonRoutes with Routeable {

  implicit val system: ActorSystem[_] = context.system
  implicit val ec: scala.concurrent.ExecutionContext = context.executionContext

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowGrafJson._
  import io.hacken.ext.detector.DetectorSchemaJson._
  import io.hacken.ext.detector.DetectorConfigJson._
  import io.syspulse.skel.wf.ext.engine.EngineJson._

  // ---- WorkflowSchema asks ----
  def getWorkflowSchemas(from: Option[Long], size: Option[Long], entity: String): Future[Try[WorkflowSchemas]] = registry.ask(GetWorkflowSchemas(from, size, entity, _))
  def getWorkflowSchema(id: Int, entity: String): Future[Try[WorkflowSchemaView]] = registry.ask(GetWorkflowSchema(id, entity, _))
  def createWorkflowSchema(req: WorkflowSchemaCreateReq): Future[Try[WorkflowSchema]] = registry.ask(CreateWorkflowSchema(req, _))
  def createWorkflowSchemaDsl(req: WorkflowSchemaDslReq): Future[Try[WorkflowSchema]] = registry.ask(CreateWorkflowSchemaDsl(req, _))
  def updateWorkflowSchema(id: Int, req: WorkflowSchemaUpdateReq): Future[Try[WorkflowSchema]] = registry.ask(UpdateWorkflowSchema(id, req, _))
  def deleteWorkflowSchema(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteWorkflowSchema(id, _))

  // ---- WorkflowConfig asks ----
  def getWorkflowConfigs(from: Option[Long], size: Option[Long], entity: String): Future[Try[WorkflowConfigs]] = registry.ask(GetWorkflowConfigs(from, size, entity, _))
  def getWorkflowConfig(id: Int, entity: String): Future[Try[WorkflowConfigView]] = registry.ask(GetWorkflowConfig(id, entity, _))
  def getWorkflowConfigByXid(xid: String): Future[Option[WorkflowConfig]] = registry.ask(GetWorkflowConfigByXid(xid, _))
  def getWorkflowConfigsByOid(oid: String): Future[Try[WorkflowConfigs]] = registry.ask(GetWorkflowConfigsByOid(oid, _))
  def resolveWorkflowConfigs(ids: Seq[String], typ: Option[String]): Future[Try[WorkflowConfigs]] = registry.ask(ResolveWorkflowConfigs(ids, typ, _))
  def createWorkflowConfig(req: WorkflowConfigCreateReq): Future[Try[WorkflowConfig]] = registry.ask(CreateWorkflowConfig(req, _))
  def createWorkflowConfigFromSchema(sid: Int, contractId: Int): Future[Try[WorkflowConfig]] = registry.ask(CreateWorkflowConfigFromSchema(sid, contractId, _))
  def setup0(tenantId: Int, projectId: Int, contractId: Int, name: String, status: String): Future[Try[WorkflowActionRes]] =
    registry.ask(Setup0(tenantId, projectId, contractId, name, status, _))
  def createWorkflowConfigDsl(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(CreateWorkflowConfigDsl(req, _))
  def assemblyWorkflowConfig(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(AssemblyWorkflowConfig(req, _))
  def assemblyWorkflowConfigLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String): Future[Try[WorkflowConfig]] = registry.ask(AssemblyWorkflowConfigLinked(req, runtime, fallbackId, _))
  def linkWorkflowConfig(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(LinkWorkflowConfig(req, _))
  def linkWorkflowConfigLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String): Future[Try[WorkflowConfig]] = registry.ask(LinkWorkflowConfigLinked(req, runtime, fallbackId, _))
  def updateWorkflowConfig(id: Int, req: WorkflowConfigUpdateReq): Future[Try[WorkflowConfig]] = registry.ask(UpdateWorkflowConfig(id, req, _))
  def deleteWorkflowConfig(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteWorkflowConfig(id, _))

  // ---- WorkflowGraf asks ----
  def getWorkflowGrafs(from: Option[Long], size: Option[Long]): Future[Try[WorkflowGrafs]] = registry.ask(GetWorkflowGrafs(from, size, _))
  def getWorkflowGraf(id: Int): Future[Try[WorkflowGraf]] = registry.ask(GetWorkflowGraf(id, _))
  def createWorkflowGraf(req: WorkflowGrafCreateReq): Future[Try[WorkflowGraf]] = registry.ask(CreateWorkflowGraf(req, _))
  def deleteWorkflowGraf(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteWorkflowGraf(id, _))

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

  // `entity` is a CSV of sections to include: graf,detector,schema (or `all`). Empty/absent -> "graf".
  // The raw value is passed through and parsed in WorkflowRegistry.parseEntities.
  private def entityMode(entity: Option[String]): String = entity.getOrElse("")

  // NOTE: error handling is centralized in Server.scala (JSON ExceptionHandler). Routes just
  // `complete(...)` the ask result - a `Future[Try[T]]` Failure (or a failed Future) is re-raised by
  // the akka-http Try/Throwable marshaller and rendered as JSON by the Server (ErrNotFound -> 404).

  // ---- engine (runtime) handlers ----
  /** Resolve the Engine for a path `{engine}` segment; only the configured engine is served. */
  private def forEngine(engineName: String)(f: Engine => Route): Route = engine match {
    case Some(e) if e.name.equalsIgnoreCase(engineName) => f(e)
    case Some(e) => complete(StatusCodes.NotFound -> s"engine not supported: '${engineName}' (configured: '${e.name}')")
    case None    => complete(StatusCodes.NotImplemented -> "no Engine configured (start with --engine=temporal://...)")
  }

  def getEngineRuntimesRoute(engineName: String, namespace: Option[String]) = get {
    forEngine(engineName) { e =>
      complete(e.getRuntimes(namespace).map(ws => EngineWorkflows(ws, ws.size.toLong)))
    }
  }

  def getEngineRuntimeRoute(engineName: String, namespace: Option[String], runtimeId: String) = get {
    forEngine(engineName) { e =>
      rejectEmptyResponse { complete(e.getRuntime(namespace, runtimeId)) }
    }
  }

  // ================================================================ schema routes
  @GET @Path("/schema") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "List WorkflowSchemas",
    parameters = Array(
      new Parameter(name = "from", in = ParameterIn.QUERY, description = "Page offset"),
      new Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size"),
      new Parameter(name = "entity", in = ParameterIn.QUERY, description = "CSV of graf,detector,schema (or all); default graf")),
    responses = Array(new ApiResponse(responseCode = "200", description = "schemas",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemas]))))))
  def getWorkflowSchemasRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?, "entity".?) { (from, size, entity) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => complete(getWorkflowSchemas(from, size, entityMode(entity)))
      }
    }
  }

  @GET @Path("/schema/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "Get WorkflowSchema by id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "schema id"),
      new Parameter(name = "entity", in = ParameterIn.QUERY, description = "CSV of graf,detector,schema (or all); default graf")),
    responses = Array(new ApiResponse(responseCode = "200", description = "schema",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemaView]))))))
  def getWorkflowSchemaRoute(id: Int) = get {
    parameter("entity".?) { entity =>
      complete(getWorkflowSchema(id, entityMode(entity)))
    }
  }

  @POST @Path("/schema") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "Create WorkflowSchema",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemaCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchema]))))))
  def createWorkflowSchemaRoute() = post {
    entity(as[WorkflowSchemaCreateReq]) { req => complete(createWorkflowSchema(req)) }
  }

  def createWorkflowSchemaDslRoute() = post {
    entity(as[WorkflowSchemaDslReq]) { req => complete(createWorkflowSchemaDsl(req)) }
  }

  def updateWorkflowSchemaRoute(id: Int) = put {
    entity(as[WorkflowSchemaUpdateReq]) { req => complete(updateWorkflowSchema(id, req)) }
  }

  def deleteWorkflowSchemaRoute(id: Int) = delete { complete(deleteWorkflowSchema(id)) }

  // ================================================================ config routes
  @GET @Path("/config") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "List WorkflowConfigs",
    parameters = Array(
      new Parameter(name = "from", in = ParameterIn.QUERY, description = "Page offset"),
      new Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size"),
      new Parameter(name = "entity", in = ParameterIn.QUERY, description = "CSV of graf,detector,schema (or all); default graf")),
    responses = Array(new ApiResponse(responseCode = "200", description = "configs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigs]))))))
  def getWorkflowConfigsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?, "entity".?) { (from, size, entity) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => complete(getWorkflowConfigs(from, size, entityMode(entity)))
      }
    }
  }

  @GET @Path("/config/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Get WorkflowConfig by id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "config id"),
      new Parameter(name = "entity", in = ParameterIn.QUERY, description = "CSV of graf,detector,schema (or all); default graf")),
    responses = Array(new ApiResponse(responseCode = "200", description = "config",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigView]))))))
  def getWorkflowConfigRoute(id: Int) = get {
    parameter("entity".?) { entity =>
      complete(getWorkflowConfig(id, entityMode(entity)))
    }
  }

  def getWorkflowConfigByXidRoute(xid: String) = get { rejectEmptyResponse { complete(getWorkflowConfigByXid(xid)) } }
  def getWorkflowConfigsByOidRoute(oid: String) = get { complete(getWorkflowConfigsByOid(oid)) }

  /** Split a comma-separated `ids` path segment into a clean list. */
  private def splitIds(csv: String): Seq[String] =
    csv.split(",").map(_.trim).filter(_.nonEmpty).toSeq

  @GET @Path("/config/resolve/{ids}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Resolve WorkflowConfig(s) + all DetectorConfigs by runtimeId or workflowId (statuses taken LIVE from the Engine; UNRESOLVED when not present)",
    parameters = Array(
      new Parameter(name = "ids", in = ParameterIn.PATH, description = "comma-separated runtimeId (UUID) and/or workflowId list"),
      new Parameter(name = "type", in = ParameterIn.QUERY, description = "force resolution mode: 'rid' (runtimeId/xid) or 'wid' (workflowId); default auto-detect")),
    responses = Array(new ApiResponse(responseCode = "200", description = "configs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigs]))))))
  def getWorkflowConfigsResolveRoute(ids: Seq[String], typ: Option[String]) = get {
    // the Engine query + live status mapping happens in WorkflowRegistry.ResolveWorkflowConfigs
    complete(resolveWorkflowConfigs(ids, typ))
  }

  @POST @Path("/config") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Create WorkflowConfig from WorkflowSchema",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigRoute() = post {
    entity(as[WorkflowConfigCreateReq]) { req => complete(createWorkflowConfig(req)) }
  }

  @POST @Path("/config/schema/{sid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Create a WorkflowConfig from a WorkflowSchema id (composed of DetectorConfig; ids assigned by the store)",
    parameters = Array(
      new Parameter(name = "sid", in = ParameterIn.PATH, description = "WorkflowSchema id"),
      new Parameter(name = "contractId", in = ParameterIn.QUERY, description = "contract id to place the DetectorConfigs under (default 0)")),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigFromSchemaRoute(sid: Int) = post {
    parameter("contractId".as[Int].?) { contractId =>
      complete(createWorkflowConfigFromSchema(sid, contractId.getOrElse(0)))
    }
  }

  @POST @Path("/setup0") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Bootstrap the default placement: tenant -> project -> contract (idempotent)",
    parameters = Array(
      new Parameter(name = "tenantId", in = ParameterIn.QUERY, description = "tenant id (default 0)"),
      new Parameter(name = "projectId", in = ParameterIn.QUERY, description = "project id (default 0)"),
      new Parameter(name = "contractId", in = ParameterIn.QUERY, description = "contract id (default 0)"),
      new Parameter(name = "name", in = ParameterIn.QUERY, description = "name for tenant/project/contract (default 'setup0')"),
      new Parameter(name = "status", in = ParameterIn.QUERY, description = "tenant status (default 'DISABLED')")),
    responses = Array(new ApiResponse(responseCode = "200", description = "ok",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowActionRes]))))))
  def setup0Route() = post {
    parameters("tenantId".as[Int].?, "projectId".as[Int].?, "contractId".as[Int].?, "name".?, "status".?) { (t, p, c, n, s) =>
      complete(setup0(t.getOrElse(0), p.getOrElse(0), c.getOrElse(0), n.getOrElse("setup0"), s.getOrElse("DISABLED")))
    }
  }

  def createWorkflowConfigDslRoute() = post {
    entity(as[WorkflowConfigDslReq]) { req => complete(createWorkflowConfigDsl(req)) }
  }

  @POST @Path("/config/assembly") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Assemble a WorkflowConfig from an Assembly DSL pipeline (same as the `assembly` command)",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigDslReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "assembled",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigAssemblyRoute() = post {
    entity(as[WorkflowConfigDslReq]) { req => complete(assemblyWorkflowConfig(req)) }
  }

  @POST @Path("/temporal/assembly/{id}") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("engine"), summary = "Assemble a WorkflowConfig from DSL (creating Detectors) and link it to an existing Temporal id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Temporal runtimeId (UUID) or workflowId"),
      new Parameter(name = "ns", in = ParameterIn.QUERY, description = "namespace (default: all)")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigDslReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "assembled + linked",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def temporalAssemblyRoute(id: String) = post {
    entity(as[WorkflowConfigDslReq]) { req =>
      parameter("ns".?) { ns =>
        engine match {
          case Some(e) =>
            // resolve the Temporal id (runtimeId or workflowId) on the engine, then assembly + bind
            onComplete(TrackMapper.of(id).resolve(e, ns)) {
              case Success(runtime) => complete(assemblyWorkflowConfigLinked(req, runtime, id))
              case Failure(ex)      => complete(StatusCodes.InternalServerError -> s"engine error: ${ex.getMessage}")
            }
          case None => complete(StatusCodes.NotImplemented -> "no Engine configured (start with --engine=temporal://...)")
        }
      }
    }
  }

  @POST @Path("/config/link") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Link a WorkflowConfig from DSL referencing EXISTING DetectorConfigs by name (latest version); creates no Detector*",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigDslReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "linked",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigLinkRoute() = post {
    entity(as[WorkflowConfigDslReq]) { req => complete(linkWorkflowConfig(req)) }
  }

  @POST @Path("/temporal/link/{id}") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("engine"), summary = "Link a WorkflowConfig from DSL (existing DetectorConfigs by name) and bind it to an existing Temporal id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Temporal runtimeId (UUID) or workflowId"),
      new Parameter(name = "ns", in = ParameterIn.QUERY, description = "namespace (default: all)")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigDslReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "linked + bound",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def temporalLinkRoute(id: String) = post {
    entity(as[WorkflowConfigDslReq]) { req =>
      parameter("ns".?) { ns =>
        engine match {
          case Some(e) =>
            // resolve the Temporal id (runtimeId or workflowId) on the engine, then link-by-name + bind
            onComplete(TrackMapper.of(id).resolve(e, ns)) {
              case Success(runtime) => complete(linkWorkflowConfigLinked(req, runtime, id))
              case Failure(ex)      => complete(StatusCodes.InternalServerError -> s"engine error: ${ex.getMessage}")
            }
          case None => complete(StatusCodes.NotImplemented -> "no Engine configured (start with --engine=temporal://...)")
        }
      }
    }
  }

  def updateWorkflowConfigRoute(id: Int) = put {
    entity(as[WorkflowConfigUpdateReq]) { req => complete(updateWorkflowConfig(id, req)) }
  }

  def deleteWorkflowConfigRoute(id: Int) = delete { complete(deleteWorkflowConfig(id)) }

  // ================================================================ graf routes
  @GET @Path("/graf") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "List WorkflowGrafs",
    responses = Array(new ApiResponse(responseCode = "200", description = "grafs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGrafs]))))))
  def getWorkflowGrafsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => complete(getWorkflowGrafs(from, size))
      }
    }
  }

  @GET @Path("/graf/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "Get WorkflowGraf by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "graf id")),
    responses = Array(new ApiResponse(responseCode = "200", description = "graf",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGraf]))))))
  def getWorkflowGrafRoute(id: Int) = get { complete(getWorkflowGraf(id)) }

  @POST @Path("/graf") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "Create WorkflowGraf",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGrafCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGraf]))))))
  def createWorkflowGrafRoute() = post {
    entity(as[WorkflowGrafCreateReq]) { req => complete(createWorkflowGraf(req)) }
  }

  def deleteWorkflowGrafRoute(id: Int) = delete { complete(deleteWorkflowGraf(id)) }

  // ================================================================ detector-schema routes
  def getDetectorSchemasRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => complete(getDetectorSchemas(from, size))
      }
    }
  }
  def getDetectorSchemaRoute(id: Int) = get { complete(getDetectorSchema(id)) }
  def createDetectorSchemaRoute() = post {
    entity(as[DetectorSchemaCreateReq]) { req => complete(createDetectorSchema(req)) }
  }
  def updateDetectorSchemaRoute(id: Int) = put {
    entity(as[DetectorSchemaUpdateReq]) { req => complete(updateDetectorSchema(id, req)) }
  }
  def deleteDetectorSchemaRoute(id: Int) = delete { complete(deleteDetectorSchema(id)) }

  // ================================================================ detector-config routes
  def getDetectorConfigsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      (from, size) match {
        case (Some(_), None) | (None, Some(_)) => complete(StatusCodes.BadRequest -> "from and size must be provided together")
        case _ => complete(getDetectorConfigs(from, size))
      }
    }
  }
  def getDetectorConfigRoute(id: Int) = get { complete(getDetectorConfig(id)) }
  def createDetectorConfigRoute() = post {
    entity(as[DetectorConfigCreateReq]) { req => complete(createDetectorConfig(req)) }
  }
  def updateDetectorConfigRoute(id: Int) = put {
    entity(as[DetectorConfigUpdateReq]) { req => complete(updateDetectorConfig(id, req)) }
  }
  def deleteDetectorConfigRoute(id: Int) = delete { complete(deleteDetectorConfig(id)) }

  val corsAllow = CorsSettings(system.classicSystem)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS, HttpMethods.GET, HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE, HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    concat(
      pathPrefix("setup0") { pathEndOrSingleSlash { setup0Route() } },
      pathPrefix("schema") {
        concat(
          pathPrefix("dsl") { pathEndOrSingleSlash { createWorkflowSchemaDslRoute() } },
          pathPrefix(IntNumber) { id =>
            pathEndOrSingleSlash {
              getWorkflowSchemaRoute(id) ~ updateWorkflowSchemaRoute(id) ~ deleteWorkflowSchemaRoute(id)
            }
          },
          pathEndOrSingleSlash { getWorkflowSchemasRoute() ~ createWorkflowSchemaRoute() },
        )
      },
      pathPrefix("config") {
        concat(
          pathPrefix("dsl") { pathEndOrSingleSlash { createWorkflowConfigDslRoute() } },
          pathPrefix("schema") { pathPrefix(IntNumber) { sid => pathEndOrSingleSlash { createWorkflowConfigFromSchemaRoute(sid) } } },
          pathPrefix("assembly") { pathEndOrSingleSlash { createWorkflowConfigAssemblyRoute() } },
          pathPrefix("link") { pathEndOrSingleSlash { createWorkflowConfigLinkRoute() } },
          pathPrefix("resolve") {
            // /config/resolve/<a>,<b>,<c>[?type=rid|wid]
            pathPrefix(Segment) { csv =>
              pathEndOrSingleSlash {
                parameter("type".?) { typ => getWorkflowConfigsResolveRoute(splitIds(csv), typ) }
              }
            }
          },
          pathPrefix("xid") { pathPrefix(Segment) { xid => getWorkflowConfigByXidRoute(xid) } },
          pathPrefix("oid") { pathPrefix(Segment) { oid => getWorkflowConfigsByOidRoute(oid) } },
          pathPrefix(IntNumber) { id =>
            pathEndOrSingleSlash {
              getWorkflowConfigRoute(id) ~ updateWorkflowConfigRoute(id) ~ deleteWorkflowConfigRoute(id)
            }
          },
          pathEndOrSingleSlash { getWorkflowConfigsRoute() ~ createWorkflowConfigRoute() },
        )
      },
      pathPrefix("graf") {
        concat(
          pathPrefix(IntNumber) { id =>
            pathEndOrSingleSlash { getWorkflowGrafRoute(id) ~ deleteWorkflowGrafRoute(id) }
          },
          pathEndOrSingleSlash { getWorkflowGrafsRoute() ~ createWorkflowGrafRoute() },
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
      // Temporal-specific: POST /temporal/assembly/{id} (creates Detector*), /temporal/link/{id}
      // (references existing DetectorConfigs by name). id = runtimeId or workflowId.
      pathPrefix("temporal") {
        concat(
          pathPrefix("assembly") {
            pathPrefix(Segment) { id => pathEndOrSingleSlash { temporalAssemblyRoute(id) } }
          },
          pathPrefix("link") {
            pathPrefix(Segment) { id => pathEndOrSingleSlash { temporalLinkRoute(id) } }
          },
        )
      },
      // Engine runtime state:
      //   /engine/{engine}                        -> all workflows in all namespaces
      //   /engine/{engine}/{namespace}            -> all workflows in a namespace
      //   /engine/{engine}/{namespace}/{runtime}  -> single workflow (expanded) by runtimeId
      pathPrefix("engine") {
        pathPrefix(Segment) { engineName =>
          concat(
            pathPrefix(Segment) { namespace =>
              concat(
                pathPrefix(Segment) { runtimeId =>
                  pathEndOrSingleSlash { getEngineRuntimeRoute(engineName, Some(namespace), runtimeId) }
                },
                pathEndOrSingleSlash { getEngineRuntimesRoute(engineName, Some(namespace)) },
              )
            },
            pathEndOrSingleSlash { getEngineRuntimesRoute(engineName, None) },
          )
        }
      },
    )
  }
}
