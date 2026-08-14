package io.syspulse.skel.wf.ext.server

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Route}
import akka.http.scaladsl.model._
import io.syspulse.skel.ErrAuthorization

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

import spray.json.{JsValue, JsObject}

import io.syspulse.skel.auth.Authenticated
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers
import io.syspulse.skel.auth.ext.{ExtAuth, ExtRbacStrict, ExtRbacUser}

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.Command

import io.syspulse.skel.wf.ext.Config
import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}
import io.syspulse.skel.wf.ext.store.WorkflowRegistry
import io.syspulse.skel.wf.ext.store.WorkflowRegistry._
import io.syspulse.skel.wf.ext.store.WorkflowStore
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineWorkflows, TrackMapper}

/**
 * Workflow `ext` REST API:
 *   /api/v1/wf/ext/schema  - WorkflowSchema CRUD (+ ?entity={graf,detector,schema|all}, + /dsl, /{id}/start)
 *   /api/v1/wf/ext/config  - WorkflowConfig CRUD (+ ?entity={graf,detector,schema|all}, + /dsl, /xid, /oid, /{id}/stop, /{id}/cancel)
 *   /api/v1/wf/ext/graf    - WorkflowGraf CRUD (visual configuration)
 *   /api/v1/wf/ext/engine  - Engine runtime state (Temporal), enabled when an Engine is configured
 */
@Path("/")
class WorkflowRoutes(registry: ActorRef[Command], engine: Option[Engine] = None)(implicit context: ActorContext[_], config: Config) extends CommonRoutes with Routeable with RouteAuthorizers {

  implicit val system: ActorSystem[_] = context.system
  implicit val ec: scala.concurrent.ExecutionContext = context.executionContext

  implicit val permissions: Permissions = config.permissions match {
    case "strict" => new ExtRbacStrict(config.adminRole, config.serviceRole, config.rolesAttr)
    case "user"   => new ExtRbacUser(config.adminRole, config.serviceRole, config.rolesAttr)
    case _        => Permissions(config.permissions)
  }

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowGrafJson._
  import io.hacken.ext.detector.DetectorSchemaJson._
  import io.hacken.ext.detector.DetectorConfigJson._
  import io.syspulse.skel.wf.ext.engine.EngineJson._

  // ================================================================ authorization
  // Rules:
  //  - Schema (WorkflowSchema/DetectorSchema): GET = any authenticated user; POST/PUT/DELETE = admin|service
  //  - WorkflowConfig / DetectorConfig:
  //      * optional `?oid=` / `?pid=` API params (pid filters project; oid is authorization scope)
  //      * user: `oid` MUST be present and equal JWT owner; JWT always overrides oid for Store/create
  //      * admin|service: any oid (or omit oid = no owner filter in Store)
  //      * DetectorConfig maps oid -> contract.tenantId, pid -> contract.projectId

  /** admin & service roles may use any API / any oid. */
  private def canAccessAdmin(authn: Authenticated): Boolean =
    Permissions.isAdmin(authn) || Permissions.isService(authn)

  private def oidOpt(oid: Option[String]): Option[String] =
    oid.map(_.trim).filter(_.nonEmpty)

  /**
   * Authorize request `oid` against JWT.
   * - admin|service: always allowed (any oid, including absent)
   * - user: oid must be non-empty AND equal the JWT owner attribute (reject missing/mismatch)
   */
  private def canAccessOid(authn: Authenticated, oid: Option[String]): Boolean =
    canAccessAdmin(authn) || {
      val jwtOid = ExtAuth.getOwner(authn, config.ownerAttr).filter(_.nonEmpty)
      val reqOid = oidOpt(oid)
      reqOid.isDefined && jwtOid.isDefined && reqOid == jwtOid
    }

  /**
   * oid passed to Store / stamped on create.
   * - admin|service: request oid as-is (None = no owner filter)
   * - user: ALWAYS the JWT owner (overrides request oid after canAccessOid succeeds)
   */
  private def storeOid(authn: Authenticated, oidParam: Option[String]): Option[String] =
    if (canAccessAdmin(authn)) oidOpt(oidParam)
    else ExtAuth.getOwner(authn, config.ownerAttr).filter(_.nonEmpty)

  /** authenticated + any user (valid JWT required, no role/oid restriction). */
  private def authUser(inner: => Route): Route = authenticate()(_ => inner)

  /** authenticated + admin|service only. */
  private def authAdminService(inner: => Route): Route = authenticate()(authn => authorize(canAccessAdmin(authn))(inner))

  /** fetch a WorkflowConfig (for its oid) before authorizing a per-config operation; 404 when missing. */
  private def withConfig(id: Int, oid: Option[String], pid: Option[String])(inner: WorkflowConfig => Route): Route =
    onComplete(getWorkflowConfig(id, "", oid, pid)) {
      case Success(Success(view)) => inner(view.config)
      case Success(Failure(_))    => complete(StatusCodes.NotFound -> s"WorkflowConfig not found: ${id}")
      case Failure(e)             => complete(StatusCodes.InternalServerError -> e.getMessage)
    }

  /** authenticated + oid auth for stop/cancel/signal (requires ?oid= for users). */
  private def authConfig(id: Int, oidParam: Option[String], pid: Option[String] = None)(inner: => Route): Route =
    authenticate()(authn => authorize(canAccessOid(authn, oidParam)) {
      withConfig(id, storeOid(authn, oidParam), pid)(_ => inner)
    })

  // ---- WorkflowSchema asks ----
  def getWorkflowSchemas(from: Option[Long], size: Option[Long], entity: String): Future[Try[WorkflowSchemas]] = registry.ask(GetWorkflowSchemas(from, size, entity, _))
  def getWorkflowSchema(id: Int, entity: String): Future[Try[WorkflowSchemaView]] = registry.ask(GetWorkflowSchema(id, entity, _))
  def createWorkflowSchema(req: WorkflowSchemaCreateReq): Future[Try[WorkflowSchema]] = registry.ask(CreateWorkflowSchema(req, _))
  def createWorkflowSchemaDsl(req: WorkflowSchemaDslReq): Future[Try[WorkflowSchema]] = registry.ask(CreateWorkflowSchemaDsl(req, _))
  def updateWorkflowSchema(id: Int, req: WorkflowSchemaUpdateReq): Future[Try[WorkflowSchema]] = registry.ask(UpdateWorkflowSchema(id, req, _))
  def deleteWorkflowSchema(id: Int): Future[WorkflowActionRes] = registry.ask(DeleteWorkflowSchema(id, _))
  def startWorkflowSchema(id: Int, taskQueue: Option[String], input: Option[String], config: Option[JsObject], wid: Option[String], ns: Option[String], oid: Option[String], pid: Option[String], author: Option[String]): Future[Try[WorkflowConfigs]] = registry.ask(StartWorkflowSchema(id, taskQueue, input, config, wid, ns, oid, pid, author, _))

  // ---- WorkflowConfig asks ----
  def getWorkflowConfigs(from: Option[Long], size: Option[Long], entity: String, oid: Option[String], pid: Option[String]): Future[Try[WorkflowConfigs]] = registry.ask(GetWorkflowConfigs(from, size, entity, oid, pid, _))
  def getWorkflowConfig(id: Int, entity: String, oid: Option[String], pid: Option[String]): Future[Try[WorkflowConfigView]] = registry.ask(GetWorkflowConfig(id, entity, oid, pid, _))
  def getWorkflowConfigByXid(xid: String): Future[Option[WorkflowConfig]] = registry.ask(GetWorkflowConfigByXid(xid, _))
  def getWorkflowConfigsByOid(oid: String, pid: Option[String]): Future[Try[WorkflowConfigs]] = registry.ask(GetWorkflowConfigsByOid(oid, pid, _))
  def resolveWorkflowConfigs(ids: Seq[String], typ: Option[String], oid: Option[String]): Future[Try[WorkflowConfigs]] =
    registry.ask(ResolveWorkflowConfigs(ids, typ, oid, _))
  def createWorkflowConfig(req: WorkflowConfigCreateReq): Future[Try[WorkflowConfig]] = registry.ask(CreateWorkflowConfig(req, _))
  def createWorkflowConfigFromSchema(sid: Int, contractId: Int, oid: Option[String] = None): Future[Try[WorkflowConfig]] = registry.ask(CreateWorkflowConfigFromSchema(sid, contractId, oid, _))
  def setup0(tenantId: Int, projectId: Int, contractId: Int, name: String, status: String): Future[Try[WorkflowActionRes]] =
    registry.ask(Setup0(tenantId, projectId, contractId, name, status, _))
  def createWorkflowConfigDsl(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(CreateWorkflowConfigDsl(req, _))
  def assemblyWorkflowConfig(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(AssemblyWorkflowConfig(req, _))
  def assemblyWorkflowConfigLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String): Future[Try[WorkflowConfig]] = registry.ask(AssemblyWorkflowConfigLinked(req, runtime, fallbackId, _))
  def linkWorkflowConfig(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(LinkWorkflowConfig(req, _))
  def linkWorkflowConfigLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String): Future[Try[WorkflowConfig]] = registry.ask(LinkWorkflowConfigLinked(req, runtime, fallbackId, _))
  def updateWorkflowConfig(id: Int, req: WorkflowConfigUpdateReq, oid: Option[String], pid: Option[String]): Future[Try[WorkflowConfig]] = registry.ask(UpdateWorkflowConfig(id, req, oid, pid, _))
  def deleteWorkflowConfig(id: Int, oid: Option[String], pid: Option[String]): Future[WorkflowActionRes] = registry.ask(DeleteWorkflowConfig(id, oid, pid, _))
  def stopWorkflowConfig(id: Int, reason: Option[String]): Future[Try[WorkflowConfig]] = registry.ask(StopWorkflowConfig(id, reason, _))
  def cancelWorkflowConfig(id: Int, reason: Option[String]): Future[Try[WorkflowConfig]] = registry.ask(CancelWorkflowConfig(id, reason, _))
  def signalWorkflowConfig(id: Int, name: String, payload: Option[String]): Future[Try[WorkflowConfig]] = registry.ask(SignalWorkflowConfig(id, name, payload, _))

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
  def getDetectorConfigs(from: Option[Long], size: Option[Long], oid: Option[String], pid: Option[String]): Future[Try[DetectorConfigs]] = registry.ask(GetDetectorConfigs(from, size, oid, pid, _))
  def getDetectorConfig(id: Int, oid: Option[String], pid: Option[String]): Future[Try[DetectorConfig]] = registry.ask(GetDetectorConfig(id, oid, pid, _))
  def createDetectorConfig(req: DetectorConfigCreateReq): Future[Try[DetectorConfig]] = registry.ask(CreateDetectorConfig(req, _))
  def updateDetectorConfig(id: Int, req: DetectorConfigUpdateReq, oid: Option[String], pid: Option[String]): Future[Try[DetectorConfig]] = registry.ask(UpdateDetectorConfig(id, req, oid, pid, _))
  def deleteDetectorConfig(id: Int, oid: Option[String], pid: Option[String]): Future[WorkflowActionRes] = registry.ask(DeleteDetectorConfig(id, oid, pid, _))

  // `entity` is a CSV of sections to include: graf,detector,schema (or `all`). Empty/absent -> "graf".
  // The raw value is passed through and parsed in WorkflowRegistry.parseEntities.
  private def entityMode(entity: Option[String]): String = entity.getOrElse("")

  private val DEF_PAGE_FROM = 0L
  private val DEF_PAGE_SIZE = 10L

  /** Fill missing paging params with defaults when either is set; both absent means no paging. */
  private def pageFrom(from: Option[Long], size: Option[Long]): Option[Long] =
    from.orElse(size.map(_ => DEF_PAGE_FROM))
  private def pageSize(from: Option[Long], size: Option[Long]): Option[Long] =
    size.orElse(from.map(_ => DEF_PAGE_SIZE))

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
    authUser { forEngine(engineName) { e =>
      complete(e.getRuntimes(namespace).map(ws => EngineWorkflows(ws, ws.size.toLong)))
    } }
  }

  def getEngineRuntimeRoute(engineName: String, namespace: Option[String], runtimeId: String) = get {
    authUser { forEngine(engineName) { e =>
      rejectEmptyResponse { complete(e.getRuntime(namespace, runtimeId)) }
    } }
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
      authUser { complete(getWorkflowSchemas(pageFrom(from, size), pageSize(from, size), entityMode(entity))) }
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
      authUser { complete(getWorkflowSchema(id, entityMode(entity))) }
    }
  }

  @POST @Path("/schema") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "Create WorkflowSchema",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemaCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchema]))))))
  def createWorkflowSchemaRoute() = post {
    authAdminService { entity(as[WorkflowSchemaCreateReq]) { req => complete(createWorkflowSchema(req)) } }
  }

  def createWorkflowSchemaDslRoute() = post {
    authAdminService { entity(as[WorkflowSchemaDslReq]) { req => complete(createWorkflowSchemaDsl(req)) } }
  }

  def updateWorkflowSchemaRoute(id: Int) = put {
    authAdminService { entity(as[WorkflowSchemaUpdateReq]) { req => complete(updateWorkflowSchema(id, req)) } }
  }

  def deleteWorkflowSchemaRoute(id: Int) = delete { authAdminService { complete(deleteWorkflowSchema(id)) } }

  // ================================================================ config routes
  @GET @Path("/config") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "List WorkflowConfigs",
    parameters = Array(
      new Parameter(name = "from", in = ParameterIn.QUERY, description = "Page offset"),
      new Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size"),
      new Parameter(name = "entity", in = ParameterIn.QUERY, description = "CSV of graf,detector,schema (or all); default graf"),
      new Parameter(name = "oid", in = ParameterIn.QUERY, description = "owner id (required for users, must match JWT; admin may omit or set any)"),
      new Parameter(name = "pid", in = ParameterIn.QUERY, description = "optional project id filter")),
    responses = Array(new ApiResponse(responseCode = "200", description = "configs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigs]))))))
  def getWorkflowConfigsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?, "entity".?, "oid".?, "pid".?) { (from, size, entity, oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        complete(getWorkflowConfigs(pageFrom(from, size), pageSize(from, size), entityMode(entity), storeOid(authn, oidQ), pid))
      })
    }
  }

  @GET @Path("/config/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Get WorkflowConfig by id",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "config id"),
      new Parameter(name = "entity", in = ParameterIn.QUERY, description = "CSV of graf,detector,schema (or all); default graf"),
      new Parameter(name = "oid", in = ParameterIn.QUERY, description = "owner id (required for users, must match JWT; admin may omit or set any)"),
      new Parameter(name = "pid", in = ParameterIn.QUERY, description = "optional project id filter")),
    responses = Array(new ApiResponse(responseCode = "200", description = "config",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigView]))))))
  def getWorkflowConfigRoute(id: Int) = get {
    parameters("entity".?, "oid".?, "pid".?) { (entity, oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        complete(getWorkflowConfig(id, entityMode(entity), storeOid(authn, oidQ), pid))
      })
    }
  }

  @POST @Path("/config/{id}/stop") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Stop (Temporal terminate) a WorkflowConfig's running Engine workflow; sets status=TERMINATED",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "config id"),
      new Parameter(name = "reason", in = ParameterIn.QUERY, description = "optional reason forwarded to the Engine"),
      new Parameter(name = "oid", in = ParameterIn.QUERY, description = "owner id (required for users, must match JWT)"),
      new Parameter(name = "pid", in = ParameterIn.QUERY, description = "optional project id filter")),
    responses = Array(new ApiResponse(responseCode = "200", description = "terminated + updated config",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def stopWorkflowConfigRoute(id: Int) = post {
    parameters("reason".?, "oid".?, "pid".?) { (reason, oidQ, pid) =>
      authConfig(id, oidQ, pid) { complete(stopWorkflowConfig(id, reason)) }
    }
  }

  @POST @Path("/config/{id}/cancel") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Cancel (Temporal request-cancel) a WorkflowConfig's running Engine workflow; sets status=CANCELED",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "config id"),
      new Parameter(name = "reason", in = ParameterIn.QUERY, description = "optional reason forwarded to the Engine"),
      new Parameter(name = "oid", in = ParameterIn.QUERY, description = "owner id (required for users, must match JWT)"),
      new Parameter(name = "pid", in = ParameterIn.QUERY, description = "optional project id filter")),
    responses = Array(new ApiResponse(responseCode = "200", description = "cancel-requested + updated config",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def cancelWorkflowConfigRoute(id: Int) = post {
    parameters("reason".?, "oid".?, "pid".?) { (reason, oidQ, pid) =>
      authConfig(id, oidQ, pid) { complete(cancelWorkflowConfig(id, reason)) }
    }
  }

  @POST @Path("/config/{id}/signal") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Send a SIGNAL (Temporal signal) to a WorkflowConfig's running Engine workflow; optional JSON body is the signal payload",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "config id"),
      new Parameter(name = "name", in = ParameterIn.QUERY, description = "signal name (default CONTINUE)"),
      new Parameter(name = "oid", in = ParameterIn.QUERY, description = "owner id (required for users, must match JWT)"),
      new Parameter(name = "pid", in = ParameterIn.QUERY, description = "optional project id filter")),
    requestBody = new RequestBody(description = "optional JSON payload delivered to the workflow's signal handler",
      content = Array(new Content(schema = new Schema(implementation = classOf[String])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "signal sent + the config",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def signalWorkflowConfigRoute(id: Int) = post {
    parameters("name".?, "oid".?, "pid".?) { (name, oidQ, pid) =>
      authConfig(id, oidQ, pid) {
        val sig = name.map(_.trim).filter(_.nonEmpty).getOrElse("CONTINUE")
        // optional JSON body = the signal payload delivered to the workflow's handler
        entity(as[JsValue]) { body => complete(signalWorkflowConfig(id, sig, Some(body.compactPrint))) } ~
        complete(signalWorkflowConfig(id, sig, None))
      }
    }
  }

  def getWorkflowConfigByXidRoute(xid: String) = get {
    parameters("oid".?, "pid".?) { (oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        val oid = storeOid(authn, oidQ)
        onComplete(getWorkflowConfigByXid(xid)) {
          case Success(Some(c)) if WorkflowStore.owned(c.oid, c.pid, oid, pid) => complete(c)
          case Success(Some(_)) | Success(None) =>
            complete(StatusCodes.NotFound -> s"WorkflowConfig not found: xid=${xid}")
          case Failure(e) =>
            complete(StatusCodes.InternalServerError -> e.getMessage)
        }
      })
    }
  }
  def getWorkflowConfigsByOidRoute(oid: String) = get {
    parameter("pid".?) { pid =>
      authenticate()(authn => authorize(canAccessOid(authn, Some(oid))) {
        complete(getWorkflowConfigsByOid(oid, pid))
      })
    }
  }

  /** Split a comma-separated `ids` path segment into a clean list. */
  private def splitIds(csv: String): Seq[String] =
    csv.split(",").map(_.trim).filter(_.nonEmpty).toSeq

  @GET @Path("/config/resolve/{ids}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Resolve WorkflowConfig(s) + all DetectorConfigs by runtimeId, workflowId, or WorkflowConfig.id (statuses taken LIVE from the Engine; UNRESOLVED when not present)",
    parameters = Array(
      new Parameter(name = "ids", in = ParameterIn.PATH, description = "comma-separated list: runtimeId (UUID) / workflowId, or WorkflowConfig.id when type=id"),
      new Parameter(name = "type", in = ParameterIn.QUERY, description = "force resolution mode: 'rid' (runtimeId/xid), 'wid' (workflowId), or 'id' (WorkflowConfig.id); default auto-detect (rid|wid)")),
    responses = Array(new ApiResponse(responseCode = "200", description = "configs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigs]))))))
  def getWorkflowConfigsResolveRoute(ids: Seq[String], typ: Option[String]) = get {
    // no ?oid/?pid: admin -> oid=None; user -> JWT oid (Store validates ownership; foreign -> ErrAuthorization)
    authenticate()(authn =>
      onComplete(resolveWorkflowConfigs(ids, typ, storeOid(authn, None))) {
        case Success(Success(wcs))              => complete(Success(wcs): Try[WorkflowConfigs])
        case Success(Failure(_: ErrAuthorization)) => reject(AuthorizationFailedRejection)
        case Success(Failure(e))                => complete(Failure(e): Try[WorkflowConfigs])
        case Failure(e)                         => complete(StatusCodes.InternalServerError -> e.getMessage)
      }
    )
  }

  @POST @Path("/schema/{id}/start") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("schema"), summary = "Create a WorkflowConfig from a WorkflowSchema and start an Engine (Temporal) execution (WorkflowType == schema.name, WorkflowId == wid|config.title|name); sets xid=RunId and returns the resolved config",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, description = "WorkflowSchema id"),
      new Parameter(name = "tq", in = ParameterIn.QUERY, description = "Task Queue an independent worker polls; else config.meta(tq), else default"),
      new Parameter(name = "wid", in = ParameterIn.QUERY, description = "override the Temporal WorkflowId (else derived from the created config.title|name)"),
      new Parameter(name = "author", in = ParameterIn.QUERY, description = "WorkflowConfig.author; if omitted, JWT `upn` claim; else WorkflowSchema.author")),
    requestBody = new RequestBody(description = "WorkflowSchemaStartReq: optional input (Temporal payload) and optional config (replaces WorkflowConfig.config; omitted keeps the schema default)",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchemaStartReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created + started + resolved config(s)",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigs]))))))
  def startWorkflowSchemaRoute(id: Int) = post {
    parameters("tq".?, "wid".?, "ns".?, "oid".?, "pid".?, "author".?) { (tq, wid, ns, oidQ, pidQ, authorQ) =>
      // admin/service only; honor the requested oid as the created WorkflowConfig owner (storeOid)
      authenticate()(authn => authorize(canAccessAdmin(authn)) {
        val oid = storeOid(authn, oidQ)
        val pid = oidOpt(pidQ)
        // author: ?author= else JWT.upn (from() falls back to WorkflowSchema.author if still None)
        val author = oidOpt(authorQ).orElse(ExtAuth.getOwner(authn, "upn").filter(_.nonEmpty))
        entity(as[WorkflowSchemaStartReq]) { req =>
          val input = req.input.filterNot(_ == spray.json.JsNull).map(_.compactPrint)
          complete(startWorkflowSchema(id, tq, input, req.config, wid, ns, oid, pid, author))
        } ~
        complete(startWorkflowSchema(id, tq, None, None, wid, ns, oid, pid, author))
      })
    }
  }

  @POST @Path("/config") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Create WorkflowConfig from WorkflowSchema",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigRoute() = post {
    parameters("oid".?, "pid".?) { (oidQ, pidQ) =>
      entity(as[WorkflowConfigCreateReq]) { req0 =>
        authenticate()(authn => {
          // authorize against ?oid (or body oid); JWT always overrides oid for users
          val oidForAuth = oidQ.orElse(req0.oid)
          authorize(canAccessOid(authn, oidForAuth)) {
            val req = req0.copy(oid = storeOid(authn, oidForAuth), pid = pidQ.orElse(req0.pid))
            complete(createWorkflowConfig(req))
          }
        })
      }
    }
  }

  @POST @Path("/config/schema/{sid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Create a WorkflowConfig from a WorkflowSchema id (composed of DetectorConfig; ids assigned by the store)",
    parameters = Array(
      new Parameter(name = "sid", in = ParameterIn.PATH, description = "WorkflowSchema id"),
      new Parameter(name = "contractId", in = ParameterIn.QUERY, description = "contract id to place the DetectorConfigs under (default 0)")),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigFromSchemaRoute(sid: Int) = post {
    parameters("contractId".as[Int].?, "oid".?) { (contractId, oidQ) =>
      // admin/service only; honor the requested oid as the created WorkflowConfig owner (storeOid)
      authenticate()(authn => authorize(canAccessAdmin(authn)) {
        complete(createWorkflowConfigFromSchema(sid, contractId.getOrElse(0), storeOid(authn, oidQ)))
      })
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
      authAdminService { complete(setup0(t.getOrElse(0), p.getOrElse(0), c.getOrElse(0), n.getOrElse("setup0"), s.getOrElse("DISABLED"))) }
    }
  }

  def createWorkflowConfigDslRoute() = post {
    authAdminService { entity(as[WorkflowConfigDslReq]) { req => complete(createWorkflowConfigDsl(req)) } }
  }

  @POST @Path("/config/assembly") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Assemble a WorkflowConfig from an Assembly DSL pipeline (same as the `assembly` command)",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigDslReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "assembled",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigAssemblyRoute() = post {
    authAdminService { entity(as[WorkflowConfigDslReq]) { req => complete(assemblyWorkflowConfig(req)) } }
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
    authAdminService {
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
  }

  @POST @Path("/config/link") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Link a WorkflowConfig from DSL referencing EXISTING DetectorConfigs by name (latest version); creates no Detector*",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigDslReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "linked",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createWorkflowConfigLinkRoute() = post {
    authAdminService { entity(as[WorkflowConfigDslReq]) { req => complete(linkWorkflowConfig(req)) } }
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
    authAdminService {
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
  }

  def updateWorkflowConfigRoute(id: Int) = put {
    parameters("oid".?, "pid".?) { (oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        entity(as[WorkflowConfigUpdateReq]) { req =>
          complete(updateWorkflowConfig(id, req, storeOid(authn, oidQ), pid))
        }
      })
    }
  }

  def deleteWorkflowConfigRoute(id: Int) = delete {
    parameters("oid".?, "pid".?) { (oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        complete(deleteWorkflowConfig(id, storeOid(authn, oidQ), pid))
      })
    }
  }

  // ================================================================ graf routes
  @GET @Path("/graf") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "List WorkflowGrafs",
    responses = Array(new ApiResponse(responseCode = "200", description = "grafs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGrafs]))))))
  def getWorkflowGrafsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      authUser { complete(getWorkflowGrafs(pageFrom(from, size), pageSize(from, size))) }
    }
  }

  @GET @Path("/graf/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "Get WorkflowGraf by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "graf id")),
    responses = Array(new ApiResponse(responseCode = "200", description = "graf",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGraf]))))))
  def getWorkflowGrafRoute(id: Int) = get { authUser { complete(getWorkflowGraf(id)) } }

  @POST @Path("/graf") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("graf"), summary = "Create WorkflowGraf",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGrafCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "created",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowGraf]))))))
  def createWorkflowGrafRoute() = post {
    authAdminService { entity(as[WorkflowGrafCreateReq]) { req => complete(createWorkflowGraf(req)) } }
  }

  def deleteWorkflowGrafRoute(id: Int) = delete { authAdminService { complete(deleteWorkflowGraf(id)) } }

  // ================================================================ detector-schema routes
  def getDetectorSchemasRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?) { (from, size) =>
      authUser { complete(getDetectorSchemas(pageFrom(from, size), pageSize(from, size))) }
    }
  }
  def getDetectorSchemaRoute(id: Int) = get { authUser { complete(getDetectorSchema(id)) } }
  def createDetectorSchemaRoute() = post {
    authAdminService { entity(as[DetectorSchemaCreateReq]) { req => complete(createDetectorSchema(req)) } }
  }
  def updateDetectorSchemaRoute(id: Int) = put {
    authAdminService { entity(as[DetectorSchemaUpdateReq]) { req => complete(updateDetectorSchema(id, req)) } }
  }
  def deleteDetectorSchemaRoute(id: Int) = delete { authAdminService { complete(deleteDetectorSchema(id)) } }

  // ================================================================ detector-config routes
  // Same JWT oid/pid rules as WorkflowConfig; Store matches contract.tenantId / contract.projectId.
  def getDetectorConfigsRoute() = get {
    parameters("from".as[Long].?, "size".as[Long].?, "oid".?, "pid".?) { (from, size, oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        complete(getDetectorConfigs(pageFrom(from, size), pageSize(from, size), storeOid(authn, oidQ), pid))
      })
    }
  }
  def getDetectorConfigRoute(id: Int) = get {
    parameters("oid".?, "pid".?) { (oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        complete(getDetectorConfig(id, storeOid(authn, oidQ), pid))
      })
    }
  }
  def createDetectorConfigRoute() = post {
    parameters("oid".?, "pid".?) { (oidQ, pidQ) =>
      entity(as[DetectorConfigCreateReq]) { req0 =>
        authenticate()(authn => {
          val oidForAuth = oidQ.orElse(req0.oid)
          authorize(canAccessOid(authn, oidForAuth)) {
            val req = req0.copy(oid = storeOid(authn, oidForAuth), pid = pidQ.orElse(req0.pid))
            complete(createDetectorConfig(req))
          }
        })
      }
    }
  }
  def updateDetectorConfigRoute(id: Int) = put {
    parameters("oid".?, "pid".?) { (oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        entity(as[DetectorConfigUpdateReq]) { req =>
          complete(updateDetectorConfig(id, req, storeOid(authn, oidQ), pid))
        }
      })
    }
  }
  def deleteDetectorConfigRoute(id: Int) = delete {
    parameters("oid".?, "pid".?) { (oidQ, pid) =>
      authenticate()(authn => authorize(canAccessOid(authn, oidQ)) {
        complete(deleteDetectorConfig(id, storeOid(authn, oidQ), pid))
      })
    }
  }

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
            pathPrefix("start") { pathEndOrSingleSlash { startWorkflowSchemaRoute(id) } } ~
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
            pathPrefix("stop")   { pathEndOrSingleSlash { stopWorkflowConfigRoute(id) } } ~
            pathPrefix("cancel") { pathEndOrSingleSlash { cancelWorkflowConfigRoute(id) } } ~
            pathPrefix("signal") { pathEndOrSingleSlash { signalWorkflowConfigRoute(id) } } ~
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
