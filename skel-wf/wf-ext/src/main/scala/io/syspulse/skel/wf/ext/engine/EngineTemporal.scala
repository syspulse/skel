package io.syspulse.skel.wf.ext.engine

import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.mutable

import com.typesafe.scalalogging.Logger
import com.google.protobuf.Timestamp

import io.temporal.serviceclient.{WorkflowServiceStubs, WorkflowServiceStubsOptions}
import io.temporal.api.workflowservice.v1.{
  ListWorkflowExecutionsRequest,
  ListNamespacesRequest,
  GetWorkflowExecutionHistoryRequest,
  StartWorkflowExecutionRequest,
  DescribeWorkflowExecutionRequest,
  DescribeTaskQueueRequest,
  TerminateWorkflowExecutionRequest,
  RequestCancelWorkflowExecutionRequest,
  SignalWorkflowExecutionRequest
}
import io.temporal.api.common.v1.{WorkflowExecution, WorkflowType, Payload, Payloads, Memo}
import io.temporal.api.taskqueue.v1.TaskQueue
import com.google.protobuf.ByteString
import io.temporal.api.workflow.v1.{WorkflowExecutionInfo => TWorkflowExecutionInfo}
import io.temporal.api.history.v1.HistoryEvent
import io.temporal.api.enums.v1.{EventType, TaskQueueType}

object EngineTemporal {
  val DEFAULT_NAMESPACE = "default"
}

// ============================================================================
// EngineTemporal
//
// Temporal implementation of the Engine abstraction. Read-only: it observes runtime
// state via the WorkflowService gRPC stubs only (list / history). No WorkflowClient,
// no DataConverter, no typed queries - so wf-ext stays fully decoupled from the
// worker/workflow implementation living in wf-temporal.
//
// Mapping is EVENT-HISTORY based:
//   - workflow status  <- visibility WorkflowExecutionStatus
//   - activity states  <- ActivityTask{Scheduled,Started,Completed,Failed,TimedOut,Canceled}
//   - child workflows  <- StartChildWorkflowExecutionInitiated + ChildWorkflowExecution{Started,...}
//                         (children share the parent WorkflowId prefix, own their RunId)
// ============================================================================
class EngineTemporal(uri: String, override val url: Option[String] = None, maxChildDepth: Int = 3)(implicit ec: ExecutionContext) extends Engine {
  private val log = Logger(getClass.getName)

  private val t = TemporalURI(uri)

  val name: String = Engine.ENGINE_TEMPORAL


  // internal system namespace never surfaced to callers
  private val SYSTEM_NAMESPACE = "temporal-system"
  private val HISTORY_PAGE = 1000

  // ---------------------------------------------------------------- connection
  private def createSslContext(): Option[io.grpc.netty.shaded.io.netty.handler.ssl.SslContext] = t.tls match {
    case Some("ignore") =>
      log.warn("TLS certificate validation DISABLED (tls=ignore)")
      Some(io.temporal.serviceclient.SimpleSslContextBuilder.newBuilder(null, null).setUseInsecureTrustManager(true).build())
    case Some("cert") =>
      Some(io.temporal.serviceclient.SimpleSslContextBuilder.newBuilder(null, null).build())
    case _ => None
  }

  private val serviceOptions = {
    val builder = WorkflowServiceStubsOptions.newBuilder()
      .setTarget(t.target)
      .setEnableKeepAlive(t.enableKeepAlive)
      .setKeepAliveTime(java.time.Duration.ofMillis(t.keepAliveTime))
      .setKeepAliveTimeout(java.time.Duration.ofMillis(t.keepAliveTimeout))
      .setRpcTimeout(java.time.Duration.ofMillis(t.rpcTimeout))

    createSslContext().foreach(builder.setSslContext)

    t.auth.foreach { token =>
      val supplier = new io.temporal.authorization.AuthorizationTokenSupplier {
        override def supply(): String = s"Bearer $token"
      }
      builder.addGrpcMetadataProvider(new io.temporal.authorization.AuthorizationGrpcMetadataProvider(supplier))
    }
    builder.build()
  }

  private lazy val service = {
    log.info(s"Connecting -> ${t.target} (ns=${t.namespace})")
    WorkflowServiceStubs.newServiceStubs(serviceOptions)
  }

  private def stub = service.blockingStub()

  /**
   * Wrap a blocking Temporal gRPC interaction so EVERY failure is logged at the source (with the
   * operation + target context) before it propagates. Downstream code may still `recover` and degrade
   * gracefully, but the exception is never invisible in the logs.
   */
  private def call[T](op: String)(f: => T): T =
    try f
    catch {
      case e: Throwable =>
        log.error(s"Temporal API [${op}] failed @ ${t.target} (ns=${t.namespace}): ${e.getMessage}", e)
        throw e
    }

  // ---------------------------------------------------------------- helpers
  private def millis(ts: Timestamp): Long = ts.getSeconds * 1000L + ts.getNanos / 1000000L

  /** Decode a Temporal result `Payloads` to its raw JSON string (single result payload; json/plain data). */
  private def payloadResult(p: Payloads): Option[String] =
    p.getPayloadsList.asScala.headOption.map(_.getData.toStringUtf8).map(_.trim).filter(_.nonEmpty)

  /** Encode a raw JSON string as a Temporal `json/plain` Payload (readable by any default DataConverter). */
  private def jsonPayload(js: String): Payload =
    Payload.newBuilder()
      .putMetadata("encoding", ByteString.copyFromUtf8("json/plain"))
      .setData(ByteString.copyFromUtf8(js))
      .build()

  private def toSummary(info: TWorkflowExecutionInfo, ns: String): EngineWorkflow = {
    val startedAt = if (info.hasStartTime) Some(millis(info.getStartTime)) else None
    val closedAt  = if (info.hasCloseTime) Some(millis(info.getCloseTime)) else None
    val parentId  = if (info.hasParentExecution) Some(info.getParentExecution.getWorkflowId) else None
    val tq        = Option(info.getTaskQueue).map(_.trim).filter(_.nonEmpty)
    EngineWorkflow(
      id = info.getExecution.getWorkflowId,
      runtimeId = info.getExecution.getRunId,
      name = info.getType.getName,
      status = EngineStatus.fromTemporalWorkflow(info.getStatus.name()),
      namespace = ns,
      startedAt = startedAt,
      closedAt = closedAt,
      taskQueue = tq,
      parentId = parentId,
    )
  }

  /** List executions in a single namespace (summary level only). */
  private def listInNamespace(ns: String, query: String, pageSize: Int): Future[Seq[EngineWorkflow]] = Future {
    val reqB = ListWorkflowExecutionsRequest.newBuilder().setNamespace(ns).setPageSize(pageSize)
    if (query.nonEmpty) reqB.setQuery(query)
    val resp = call(s"listWorkflowExecutions ns=${ns} query='${query}'") { stub.listWorkflowExecutions(reqB.build()) }
    resp.getExecutionsList.asScala.toSeq.map(info => toSummary(info, ns))
  }

  // ---------------------------------------------------------------- namespaces
  def namespaces(): Future[Seq[String]] = Future {
    val resp = call("listNamespaces") { stub.listNamespaces(ListNamespacesRequest.newBuilder().setPageSize(100).build()) }
    resp.getNamespacesList.asScala.toSeq
      .map(_.getNamespaceInfo.getName)
      .filter(n => n != SYSTEM_NAMESPACE)
  }

  /**
   * Resolve the set of namespaces to query.
   *   None            -> ALL namespaces the server exposes (matches `/engine/temporal`)
   *   Some("*")       -> ALL namespaces
   *   Some("a,b")     -> explicit list
   *   Some(ns)        -> single namespace (matches `/engine/temporal/{namespace}`)
   */
  private def resolveNamespaces(namespace: Option[String]): Future[Seq[String]] = namespace match {
    case None | Some("*")             => namespaces()
    case Some(ns) if ns.contains(",") => Future.successful(ns.split(",").map(_.trim).filter(_.nonEmpty).toSeq)
    case Some(ns)                     => Future.successful(Seq(ns))
  }

  // ---------------------------------------------------------------- panel URL
  // Temporal UI deep-link: {base}/namespaces/{ns}/workflows/{workflowId}/{runId}. `workflowType` is not
  // part of the Temporal URL (kept in the abstract signature for engines that need it). Uses the same
  // concrete namespace `start` writes to.
  // Base: `--engine.url` when set (HTTPS panel, may differ from gRPC `--engine` URI); else derive from
  // the connection URI (TemporalURI.ui / ?ui=... / http://host:8233) — same host in local/dev.
  override def panelUri(workflowType: String, workflowId: String, runtimeId: String, ns:String = EngineTemporal.DEFAULT_NAMESPACE): Option[String] = {
    val base = url.map(_.trim).filter(_.nonEmpty).getOrElse(t.ui).stripSuffix("/")
    //val ns = writeNamespace(None)
    Some(s"${base}/namespaces/${ns}/workflows/${workflowId}/${runtimeId}")
  }

  /** Pick a SINGLE concrete namespace for a write op (start): explicit -> configured -> "default". */
  private def writeNamespace(namespace: Option[String]): String =
    namespace.map(_.trim).filter(_.nonEmpty).getOrElse {
      t.namespace match {
        case "*"                  => EngineTemporal.DEFAULT_NAMESPACE
        case n if n.contains(",") => n.split(",").head.trim
        case n                    => n
      }
    }

  // ---------------------------------------------------------------- start
  override def start(namespace: Option[String], workflowType: String, workflowId: String, taskQueue: String,
                     input: Option[String], memo: Map[String, String] = Map.empty): Future[EngineStart] = Future {
    val ns = writeNamespace(namespace)
    val reqB = StartWorkflowExecutionRequest.newBuilder()
      .setNamespace(ns)
      .setWorkflowId(workflowId)
      .setWorkflowType(WorkflowType.newBuilder().setName(workflowType).build())
      .setTaskQueue(TaskQueue.newBuilder().setName(taskQueue).build())
      .setRequestId(java.util.UUID.randomUUID().toString) // idempotency key for the start RPC
    // one JSON argument, encoded so any worker's default DataConverter can read it
    input.filter(_.nonEmpty).foreach { js =>
      reqB.setInput(Payloads.newBuilder().addPayloads(jsonPayload(js)).build())
    }
    // user metadata (Temporal Memo): each entry is a TOP-LEVEL memo field with a raw JSON value,
    // encoded json/plain so the worker reads it with its default DataConverter (e.g. Python:
    // workflow.memo_value("cid", ...) / workflow.memo_value("sid", ...)).
    if (memo.nonEmpty) {
      val memoB = Memo.newBuilder()
      memo.foreach { case (k, v) => memoB.putFields(k, jsonPayload(v)) }
      reqB.setMemo(memoB.build())
    }
    val resp = call(s"startWorkflowExecution ns=${ns} type=${workflowType} wid=${workflowId} tq=${taskQueue}") {
      stub.startWorkflowExecution(reqB.build())
    }
    log.info(s"Started workflow: type=${workflowType} wid=${workflowId} rid=${resp.getRunId} tq=${taskQueue} ns=${ns}")
    EngineStart(workflowId, resp.getRunId, ns)
  }

  // ---------------------------------------------------------------- terminate / cancel
  override def terminate(namespace: Option[String], workflowId: String, runId: Option[String], reason: Option[String]): Future[Unit] = Future {
    val ns = writeNamespace(namespace)
    val exec = WorkflowExecution.newBuilder().setWorkflowId(workflowId)
    runId.map(_.trim).filter(_.nonEmpty).foreach(exec.setRunId)
    val reqB = TerminateWorkflowExecutionRequest.newBuilder()
      .setNamespace(ns)
      .setWorkflowExecution(exec.build())
    reason.map(_.trim).filter(_.nonEmpty).foreach(reqB.setReason)
    call(s"terminateWorkflowExecution ns=${ns} wid=${workflowId} rid=${runId.getOrElse("")}") { stub.terminateWorkflowExecution(reqB.build()) }
    log.info(s"Terminated workflow: wid=${workflowId} rid=${runId.getOrElse("")} ns=${ns} reason='${reason.getOrElse("")}'")
    ()
  }

  override def cancel(namespace: Option[String], workflowId: String, runId: Option[String], reason: Option[String]): Future[Unit] = Future {
    val ns = writeNamespace(namespace)
    val exec = WorkflowExecution.newBuilder().setWorkflowId(workflowId)
    runId.map(_.trim).filter(_.nonEmpty).foreach(exec.setRunId)
    val reqB = RequestCancelWorkflowExecutionRequest.newBuilder()
      .setNamespace(ns)
      .setWorkflowExecution(exec.build())
      .setRequestId(java.util.UUID.randomUUID().toString) // idempotency key for the cancel RPC
    reason.map(_.trim).filter(_.nonEmpty).foreach(reqB.setReason)
    call(s"requestCancelWorkflowExecution ns=${ns} wid=${workflowId} rid=${runId.getOrElse("")}") { stub.requestCancelWorkflowExecution(reqB.build()) }
    log.info(s"Requested cancel of workflow: wid=${workflowId} rid=${runId.getOrElse("")} ns=${ns} reason='${reason.getOrElse("")}'")
    ()
  }

  override def signal(namespace: Option[String], workflowId: String, runId: Option[String],
                      signalName: String, payload: Option[String]): Future[Unit] = Future {
    val ns = writeNamespace(namespace)
    val exec = WorkflowExecution.newBuilder().setWorkflowId(workflowId)
    runId.map(_.trim).filter(_.nonEmpty).foreach(exec.setRunId)
    val reqB = SignalWorkflowExecutionRequest.newBuilder()
      .setNamespace(ns)
      .setWorkflowExecution(exec.build())
      .setSignalName(signalName)
      .setRequestId(java.util.UUID.randomUUID().toString) // idempotency key for the signal RPC
    // one JSON argument, encoded so any worker's default DataConverter can read it
    payload.map(_.trim).filter(_.nonEmpty).foreach { js =>
      reqB.setInput(Payloads.newBuilder().addPayloads(jsonPayload(js)).build())
    }
    call(s"signalWorkflowExecution ns=${ns} wid=${workflowId} rid=${runId.getOrElse("")} signal=${signalName}") {
      stub.signalWorkflowExecution(reqB.build())
    }
    log.info(s"Signaled workflow: wid=${workflowId} rid=${runId.getOrElse("")} ns=${ns} signal=${signalName}")
    ()
  }

  // ---------------------------------------------------------------- getRuntimes
  def getRuntimes(namespace: Option[String] = None, pageSize: Int = 100): Future[Seq[EngineWorkflow]] =
    resolveNamespaces(namespace).flatMap { nss =>
      Future.sequence(nss.map { ns =>
        // failure already logged at source by `call`; degrade this namespace to empty and continue
        listInNamespace(ns, "", pageSize).recover { case _ => Seq.empty[EngineWorkflow] }
      }).map(_.flatten)
    }

  // ---------------------------------------------------------------- getRuntime
  def getRuntime(namespace: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] = {
    // 1. locate the summary (workflowId + namespace) by RunId
    findByRunId(namespace, runtimeId).flatMap {
      case None => Future.successful(None)
      case Some(summary) =>
        // 2. expand activities + child workflows from history
        buildTree(summary, depth = 0).map(Some(_))
    }
  }

  /** Find a workflow summary by RunId across the resolved namespaces. */
  // NOTE: engine errors are NOT swallowed here - a failed query propagates (it must stay visible and be
  // reported as an ERROR, never masked as "not found"). Only an EMPTY result means genuinely not found.
  private def findByRunId(namespace: Option[String], runId: String): Future[Option[EngineWorkflow]] =
    resolveNamespaces(namespace).flatMap { nss =>
      // query each namespace for RunId; return the first match
      def loop(rest: List[String]): Future[Option[EngineWorkflow]] = rest match {
        case Nil => Future.successful(None)
        case ns :: tail =>
          listInNamespace(ns, s"RunId = '${runId}'", 1).flatMap {
            case Seq(w, _*) => Future.successful(Some(w))
            case _          => loop(tail)
          }
      }
      loop(nss.toList)
    }

  // ---------------------------------------------------------------- getRuntimeByWorkflowId
  def getRuntimeByWorkflowId(namespace: Option[String], workflowId: String): Future[Option[EngineWorkflow]] =
    findByWorkflowId(namespace, workflowId).flatMap {
      case None          => Future.successful(None)
      case Some(summary) => buildTree(summary, depth = 0).map(Some(_))
    }

  /** Find the LATEST run for a WorkflowId across the resolved namespaces (most recent startTime). */
  // NOTE: engine errors are NOT swallowed here (see findByRunId) - a failed query propagates as an error.
  private def findByWorkflowId(namespace: Option[String], workflowId: String): Future[Option[EngineWorkflow]] =
    resolveNamespaces(namespace).flatMap { nss =>
      def loop(rest: List[String]): Future[Option[EngineWorkflow]] = rest match {
        case Nil => Future.successful(None)
        case ns :: tail =>
          listInNamespace(ns, s"WorkflowId = '${workflowId}'", 100).flatMap { ws =>
            // a WorkflowId may have many runs (restarts) - pick the most recently started
            ws.sortBy(w => -w.startedAt.getOrElse(0L)).headOption match {
              case Some(w) => Future.successful(Some(w))
              case None    => loop(tail)
            }
          }
      }
      loop(nss.toList)
    }

  /** Fetch the full event history for an execution (following pagination). */
  private def fetchHistory(ns: String, workflowId: String, runId: String): Future[Seq[HistoryEvent]] = Future {
    val events = mutable.ArrayBuffer[HistoryEvent]()
    var token = com.google.protobuf.ByteString.EMPTY
    var more = true
    var guard = 0
    while (more && guard < 1000) {
      guard += 1
      val reqB = GetWorkflowExecutionHistoryRequest.newBuilder()
        .setNamespace(ns)
        .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId).setRunId(runId).build())
        .setMaximumPageSize(HISTORY_PAGE)
      if (!token.isEmpty) reqB.setNextPageToken(token)
      val resp = call(s"getWorkflowExecutionHistory ns=${ns} wid=${workflowId} rid=${runId}") { stub.getWorkflowExecutionHistory(reqB.build()) }
      events ++= resp.getHistory.getEventsList.asScala
      token = resp.getNextPageToken
      more = !token.isEmpty
    }
    events.toSeq
  }

  /**
   * Number of Workers currently polling the WORKFLOW task queue `tq` in `ns` (DescribeTaskQueue). 0 means
   * "No Workers Running" - a scheduled workflow task will never be picked up (the Temporal UI shows the
   * same warning). Temporal keeps recently-seen pollers for a short window, so 0 == none seen recently.
   */
  private def workflowPollers(ns: String, tq: String): Int = {
    val req = DescribeTaskQueueRequest.newBuilder()
      .setNamespace(ns)
      .setTaskQueue(TaskQueue.newBuilder().setName(tq).build())
      .setTaskQueueType(TaskQueueType.TASK_QUEUE_TYPE_WORKFLOW)
      .build()
    val resp = call(s"describeTaskQueue ns=${ns} tq=${tq}") { stub.describeTaskQueue(req) }
    resp.getPollersCount
  }

  /**
   * Best-effort poller count for a RUNNING workflow's task queue (None when not RUNNING / no task queue /
   * the query failed). Used to flag "No Workers Running" without failing the resolve.
   */
  private def pollersOf(w: EngineWorkflow): Future[Option[Int]] =
    if (w.status != EngineStatus.RUNNING) Future.successful(None)
    else w.taskQueue.map(_.trim).filter(_.nonEmpty) match {
      case Some(tq) => Future { workflowPollers(w.namespace, tq) }.map(Some(_)).recover { case _ => None }
      case None     => Future.successful(None)
    }

  /**
   * Query PENDING activities via DescribeWorkflowExecution. This is the ONLY place a still-RUNNING
   * workflow exposes a currently failing/retrying activity: intermediate activity failures are NOT
   * written to the event history (to avoid bloat) - they live on the pending activity's `lastFailure`.
   * Returns (activityId, attempt, lastFailure message) per pending activity.
   */
  private def describePending(ns: String, workflowId: String, runId: String): Future[Seq[(String, Int, Option[String])]] = Future {
    val req = DescribeWorkflowExecutionRequest.newBuilder()
      .setNamespace(ns)
      .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId).setRunId(runId).build())
      .build()
    val resp = call(s"describeWorkflowExecution ns=${ns} wid=${workflowId} rid=${runId}") { stub.describeWorkflowExecution(req) }
    resp.getPendingActivitiesList.asScala.toSeq.map { pa =>
      val msg = if (pa.hasLastFailure) Option(pa.getLastFailure.getMessage).map(_.trim).filter(_.nonEmpty) else None
      (pa.getActivityId, pa.getAttempt, msg)
    }
  }

  // mutable accumulators used while folding the event history
  private class ActAcc(var name: String, var id: String) {
    var status: String = EngineStatus.SCHEDULED
    var startedAt: Option[Long] = None
    var closedAt: Option[Long] = None
    var detail: Option[String] = None
  }
  private class ChildAcc(var name: String, var workflowId: String) {
    var runId: Option[String] = None
    var status: String = EngineStatus.NEW
    var startedAt: Option[Long] = None
    var closedAt: Option[Long] = None
  }

  /**
   * Build a workflow subtree: parse `summary`'s history into activities + child workflows,
   * then recurse into each child (bounded by `maxChildDepth`).
   */
  private def buildTree(summary: EngineWorkflow, depth: Int): Future[EngineWorkflow] = {
    // pending activities (best-effort) reveal a failing/retrying task while the workflow is still RUNNING
    val fPending = describePending(summary.namespace, summary.id, summary.runtimeId).recover { case _ => Seq.empty }
    // poller count (best-effort) reveals a RUNNING-but-stuck workflow: no Workers on its task queue
    val fPollers = pollersOf(summary)
    fetchHistory(summary.namespace, summary.id, summary.runtimeId).zip(fPending).zip(fPollers).flatMap { case ((events, pending), pollers) =>
      val acts = mutable.LinkedHashMap[Long, ActAcc]()     // keyed by ActivityTaskScheduled eventId
      val kids = mutable.LinkedHashMap[Long, ChildAcc]()   // keyed by StartChildWorkflowExecutionInitiated eventId
      // last Workflow-Task outcome: a failing/retrying workflow task (worker throwing while executing the
      // workflow method, bad input, non-determinism, ...) keeps the workflow RUNNING and is NOT an activity
      // event - it lives in WorkflowTaskFailed events. Cleared when a later WorkflowTaskCompleted recovers it.
      var wftFailure: Option[String] = None
      // the workflow's return value (WorkflowExecutionCompleted result payload), when the run has finished
      var wfResult: Option[String] = None

      events.foreach { e =>
        val et = millis(e.getEventTime)
        e.getEventType match {
          case EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED =>
            val a = e.getActivityTaskScheduledEventAttributes
            val acc = new ActAcc(a.getActivityType.getName, a.getActivityId)
            acts.put(e.getEventId, acc)

          case EventType.EVENT_TYPE_ACTIVITY_TASK_STARTED =>
            val a = e.getActivityTaskStartedEventAttributes
            acts.get(a.getScheduledEventId).foreach { acc => acc.status = EngineStatus.RUNNING; acc.startedAt = Some(et) }

          case EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED =>
            val a = e.getActivityTaskCompletedEventAttributes
            acts.get(a.getScheduledEventId).foreach { acc => acc.status = EngineStatus.COMPLETED; acc.closedAt = Some(et) }

          case EventType.EVENT_TYPE_ACTIVITY_TASK_FAILED =>
            val a = e.getActivityTaskFailedEventAttributes
            acts.get(a.getScheduledEventId).foreach { acc =>
              acc.status = EngineStatus.FAILED; acc.closedAt = Some(et)
              acc.detail = Try(a.getFailure.getMessage).toOption.filter(_.nonEmpty)
            }

          case EventType.EVENT_TYPE_ACTIVITY_TASK_TIMED_OUT =>
            val a = e.getActivityTaskTimedOutEventAttributes
            acts.get(a.getScheduledEventId).foreach { acc => acc.status = EngineStatus.TIMED_OUT; acc.closedAt = Some(et) }

          case EventType.EVENT_TYPE_ACTIVITY_TASK_CANCELED =>
            val a = e.getActivityTaskCanceledEventAttributes
            acts.get(a.getScheduledEventId).foreach { acc => acc.status = EngineStatus.CANCELED; acc.closedAt = Some(et) }

          case EventType.EVENT_TYPE_START_CHILD_WORKFLOW_EXECUTION_INITIATED =>
            val a = e.getStartChildWorkflowExecutionInitiatedEventAttributes
            kids.put(e.getEventId, new ChildAcc(a.getWorkflowType.getName, a.getWorkflowId))

          case EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_STARTED =>
            val a = e.getChildWorkflowExecutionStartedEventAttributes
            kids.get(a.getInitiatedEventId).foreach { c =>
              c.runId = Some(a.getWorkflowExecution.getRunId)
              c.workflowId = a.getWorkflowExecution.getWorkflowId
              c.status = EngineStatus.RUNNING
              c.startedAt = Some(et)
            }

          case EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_COMPLETED =>
            val a = e.getChildWorkflowExecutionCompletedEventAttributes
            kids.get(a.getInitiatedEventId).foreach { c => c.status = EngineStatus.COMPLETED; c.closedAt = Some(et) }

          case EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_FAILED =>
            val a = e.getChildWorkflowExecutionFailedEventAttributes
            kids.get(a.getInitiatedEventId).foreach { c => c.status = EngineStatus.FAILED; c.closedAt = Some(et) }

          case EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_TERMINATED =>
            val a = e.getChildWorkflowExecutionTerminatedEventAttributes
            kids.get(a.getInitiatedEventId).foreach { c => c.status = EngineStatus.TERMINATED; c.closedAt = Some(et) }

          case EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_CANCELED =>
            val a = e.getChildWorkflowExecutionCanceledEventAttributes
            kids.get(a.getInitiatedEventId).foreach { c => c.status = EngineStatus.CANCELED; c.closedAt = Some(et) }

          case EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_TIMED_OUT =>
            val a = e.getChildWorkflowExecutionTimedOutEventAttributes
            kids.get(a.getInitiatedEventId).foreach { c => c.status = EngineStatus.TIMED_OUT; c.closedAt = Some(et) }

          // ---- workflow-task outcome (tracks a currently failing/retrying workflow task) ----
          case EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED =>
            val a = e.getWorkflowTaskFailedEventAttributes
            val msg = Try(a.getFailure.getMessage).toOption.filter(_.nonEmpty)
              .orElse(Try(a.getCause.name()).toOption.filter(_.nonEmpty))
            wftFailure = msg.orElse(Some("workflow task failed"))

          case EventType.EVENT_TYPE_WORKFLOW_TASK_TIMED_OUT =>
            wftFailure = Some("workflow task timed out")

          case EventType.EVENT_TYPE_WORKFLOW_TASK_COMPLETED =>
            wftFailure = None // recovered: a workflow task completed after any earlier failure

          // ---- workflow execution result (the run's return value, when it completed successfully) ----
          case EventType.EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED =>
            wfResult = payloadResult(e.getWorkflowExecutionCompletedEventAttributes.getResult)

          case _ => // ignore other event types
        }
      }

      // attach a pending activity's lastFailure onto its ActAcc (matched by activityId).
      // A still-pending activity with lastFailure / attempt>1 is RETRYING, not FAILED.
      pending.foreach { case (aid, attempt, msg) =>
        val retrying = msg.isDefined || attempt > 1
        acts.values.find(_.id == aid).foreach { acc =>
          msg.foreach(m => acc.detail = Some(m))
          if (retrying && (acc.status == EngineStatus.RUNNING || acc.status == EngineStatus.SCHEDULED))
            acc.status = EngineStatus.RUNNING_RETRY
        }
      }

      val activities = acts.values.map { a =>
        EngineActivity(
          id = a.id, name = a.name, kind = EngineActivity.KIND_ACTIVITY,
          status = a.status, startedAt = a.startedAt, closedAt = a.closedAt, detail = a.detail,
        )
      }.toSeq

      // recurse into child workflows (bounded)
      val childFutures: Seq[Future[EngineWorkflow]] = kids.values.toSeq.map { c =>
        c.runId match {
          case Some(rid) if depth < maxChildDepth =>
            val childSummary = EngineWorkflow(
              id = c.workflowId, runtimeId = rid, name = c.name, status = c.status,
              namespace = summary.namespace, startedAt = c.startedAt, closedAt = c.closedAt,
              parentId = Some(summary.id),
            )
            buildTree(childSummary, depth + 1).recover { case _ => childSummary }
          case _ =>
            Future.successful(EngineWorkflow(
              id = c.workflowId, runtimeId = c.runId.getOrElse(""), name = c.name, status = c.status,
              namespace = summary.namespace, startedAt = c.startedAt, closedAt = c.closedAt,
              parentId = Some(summary.id),
            ))
        }
      }

      Future.sequence(childFutures).map { children =>
        // While RUNNING:
        //   - a pending activity with lastFailure / attempt>1, or a retrying workflow task -> RUNNING_RETRY
        //     (retries are not a failure; Temporal UI shows them as Retrying)
        //   - a history activity left FAILED, or no workers on the task queue -> RUNNING_FAILED
        val wftErrs: Seq[String] = wftFailure.map(m => s"WorkflowTask: ${m}").toSeq
        val pendingRetrying = pending.filter { case (_, attempt, msg) => msg.isDefined || attempt > 1 }
        val pendingErrs: Seq[String] = pendingRetrying.collect { case (aid, _, Some(msg)) =>
          val nm = acts.values.find(_.id == aid).map(_.name).getOrElse(aid)
          s"${nm}: ${msg}"
        }
        val historyErrs: Seq[String] = activities.filter(_.status == EngineStatus.FAILED)
          .map(a => s"${a.name}: ${a.detail.getOrElse("failed")}")
        // "No Workers Running": RUNNING workflow whose task queue has zero pollers (stuck - no worker)
        val noWorkersErr: Seq[String] = pollers match {
          case Some(0) => Seq(s"No Workers Running: there are no Workers polling the ${summary.taskQueue.getOrElse("")} Task Queue")
          case _       => Seq.empty
        }
        val retrying = pendingRetrying.nonEmpty || wftFailure.isDefined
        val retryErrs = pendingErrs ++ wftErrs
        val hardErrs  = historyErrs ++ noWorkersErr
        val errAll    = retryErrs ++ hardErrs
        val (wfStatus, errMeta) =
          if (summary.status == EngineStatus.RUNNING && retrying)
            (EngineStatus.RUNNING_RETRY, if (errAll.nonEmpty) Map("err" -> errAll.mkString(" | ")) else Map.empty[String, String])
          else if (summary.status == EngineStatus.RUNNING && hardErrs.nonEmpty)
            (EngineStatus.RUNNING_FAILED, Map("err" -> hardErrs.mkString(" | ")))
          else (summary.status, Map.empty[String, String])
        // carry the completed run's return value into meta.result (raw JSON string), when present
        val meta = wfResult.map(r => errMeta + ("result" -> r)).getOrElse(errMeta)
        summary.copy(status = wfStatus, activities = activities, children = children, meta = meta)
      }
    }
  }

  def close(): Unit = {
    log.debug(s"Shutdown: ${t.target}")
    Try(service.shutdown())
  }
}
