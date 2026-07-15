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
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineWorkflows, TrackMapper, EngineMapper}

/**
 * Workflow `ext` REST API:
 *   /api/v1/wf/ext/schema  - WorkflowSchema CRUD (+ ?detector={id|full}, + /dsl)
 *   /api/v1/wf/ext/config  - WorkflowConfig CRUD (+ ?detector={id|full}, + /dsl, /xid, /oid)
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
  def resolveConfigs(ids: Seq[String], typ: Option[String]): Future[Try[WorkflowConfigs]] = registry.ask(ResolveConfigs(ids, typ, _))
  def createConfig(req: WorkflowConfigCreateReq): Future[Try[WorkflowConfig]] = registry.ask(CreateConfig(req, _))
  def createConfigDsl(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(CreateConfigDsl(req, _))
  def assemblyConfig(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(AssemblyConfig(req, _))
  def assemblyLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String): Future[Try[WorkflowConfig]] = registry.ask(AssemblyLinked(req, runtime, fallbackId, _))
  def linkConfig(req: WorkflowConfigDslReq): Future[Try[WorkflowConfig]] = registry.ask(LinkConfig(req, _))
  def linkLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String): Future[Try[WorkflowConfig]] = registry.ask(LinkLinked(req, runtime, fallbackId, _))
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

  // ---- engine (runtime) handlers ----
  /** Resolve the Engine for a path `{engine}` segment; only the configured engine is served. */
  private def forEngine(engineName: String)(f: Engine => Route): Route = engine match {
    case Some(e) if e.name.equalsIgnoreCase(engineName) => f(e)
    case Some(e) => complete(StatusCodes.NotFound -> s"engine not supported: '${engineName}' (configured: '${e.name}')")
    case None    => complete(StatusCodes.NotImplemented -> "no Engine configured (start with --engine=temporal://...)")
  }

  private def completeFuture[T](f: Future[T])(implicit m: ToResponseMarshaller[T]): Route =
    onComplete(f) {
      case Success(v) => complete(v)
      case Failure(e) => complete(StatusCodes.InternalServerError -> s"engine error: ${e.getMessage}")
    }

  def getEngineRuntimesRoute(engineName: String, namespace: Option[String]) = get {
    forEngine(engineName) { e =>
      completeFuture(e.getRuntimes(namespace).map(ws => EngineWorkflows(ws, ws.size.toLong)))
    }
  }

  def getEngineRuntimeRoute(engineName: String, namespace: Option[String], runtimeId: String) = get {
    forEngine(engineName) { e =>
      onComplete(e.getRuntime(namespace, runtimeId)) {
        case Success(Some(w)) => complete(w)
        case Success(None)    => complete(StatusCodes.NotFound -> s"runtime not found: ${runtimeId}")
        case Failure(ex)      => complete(StatusCodes.InternalServerError -> s"engine error: ${ex.getMessage}")
      }
    }
  }


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

  /** Split a comma-separated `ids` path segment into a clean list. */
  private def splitIds(csv: String): Seq[String] =
    csv.split(",").map(_.trim).filter(_.nonEmpty).toSeq

  /** Resolve the live runtime for a config: by workflowId (meta.wid, latest run) if present, else by xid. */
  private def resolveRuntime(e: Engine, c: WorkflowConfig): Future[Option[EngineWorkflow]] = {
    val f = c.meta.flatMap(_.get("wid")).map(_.toString) match {
      case Some(wid) => e.getRuntimeByWorkflowId(None, wid)
      case None      => c.xid.map(x => e.getRuntime(None, x)).getOrElse(Future.successful(None))
    }
    f.recover { case _ => None }
  }

  /**
   * Enrich resolved WorkflowConfig(s) with live engine data: map each config's runtime state onto
   * its status and its DetectorConfigs' statuses. No-op when no Engine is configured or the runtime
   * can't be resolved (the stored objects are returned unchanged).
   */
  private def enrichWithEngine(cfgs: WorkflowConfigs): Future[WorkflowConfigs] = engine match {
    case None => Future.successful(cfgs)
    case Some(e) =>
      val detectorsInt: Map[Int, DetectorConfig] = cfgs.detectors.getOrElse(Map()).map { case (k, v) => k.toInt -> v }
      Future.traverse(cfgs.configs) { c =>
        resolveRuntime(e, c).map {
          case Some(w) =>
            val view = EngineMapper.map(w, Some(c), detectorsInt)
            val stepStatus = view.steps.flatMap(s => s.cid.map(_ -> s.status)).toMap
            (c.copy(status = view.status), stepStatus)
          case None => (c, Map.empty[Int, String])
        }
      }.map { results =>
        val newConfigs = results.map(_._1)
        val stepStatusAll = results.flatMap(_._2).toMap                 // cid -> live status
        val newDetectors = detectorsInt.map { case (cid, dc) =>
          cid.toString -> stepStatusAll.get(cid).map(st => dc.copy(status = st)).getOrElse(dc)
        }
        WorkflowConfigs(newConfigs, newConfigs.size.toLong, Some(newDetectors))
      }
  }

  @GET @Path("/config/resolve/{ids}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Resolve WorkflowConfig(s) + all DetectorConfigs by runtimeId or workflowId (with live engine-mapped statuses)",
    parameters = Array(
      new Parameter(name = "ids", in = ParameterIn.PATH, description = "comma-separated runtimeId (UUID) and/or workflowId list"),
      new Parameter(name = "type", in = ParameterIn.QUERY, description = "force resolution mode: 'rid' (runtimeId/xid) or 'wid' (workflowId); default auto-detect")),
    responses = Array(new ApiResponse(responseCode = "200", description = "configs",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigs]))))))
  def getConfigsResolveRoute(ids: Seq[String], typ: Option[String]) = get {
    // resolve stored configs, then (if an Engine is configured) overlay live workflow/step statuses
    val f: Future[Try[WorkflowConfigs]] = resolveConfigs(ids, typ).flatMap {
      case Success(cfgs) => enrichWithEngine(cfgs).map(Success(_))
      case other         => Future.successful(other)
    }
    completeTry(f)
  }

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

  @POST @Path("/config/assembly") @Consumes(Array(MediaType.APPLICATION_JSON)) @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Assemble a WorkflowConfig from an Assembly DSL pipeline (same as the `assembly` command)",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfigDslReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "assembled",
      content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowConfig]))))))
  def createConfigAssemblyRoute() = post {
    entity(as[WorkflowConfigDslReq]) { req => completeTry(assemblyConfig(req)) }
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
              case Success(runtime) => completeTry(assemblyLinked(req, runtime, id))
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
  def createConfigLinkRoute() = post {
    entity(as[WorkflowConfigDslReq]) { req => completeTry(linkConfig(req)) }
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
              case Success(runtime) => completeTry(linkLinked(req, runtime, id))
              case Failure(ex)      => complete(StatusCodes.InternalServerError -> s"engine error: ${ex.getMessage}")
            }
          case None => complete(StatusCodes.NotImplemented -> "no Engine configured (start with --engine=temporal://...)")
        }
      }
    }
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
          pathPrefix("assembly") { pathEndOrSingleSlash { createConfigAssemblyRoute() } },
          pathPrefix("link") { pathEndOrSingleSlash { createConfigLinkRoute() } },
          pathPrefix("resolve") {
            // /config/resolve/<a>,<b>,<c>[?type=rid|wid]
            pathPrefix(Segment) { csv =>
              pathEndOrSingleSlash {
                parameter("type".?) { typ => getConfigsResolveRoute(splitIds(csv), typ) }
              }
            }
          },
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
