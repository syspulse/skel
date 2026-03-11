package io.syspulse.skel.wf.temporal

import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, ExecutionContext}

import io.temporal.client.{WorkflowClient, WorkflowStub}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.api.workflowservice.v1.{ListWorkflowExecutionsRequest, RegisterNamespaceRequest}
import io.temporal.api.enums.v1.{WorkflowExecutionStatus, IndexedValueType}
import io.temporal.api.operatorservice.v1.AddSearchAttributesRequest
import com.typesafe.scalalogging.Logger

case class WorkflowExecutionInfo(
  workflowId: String,
  runId: String,
  workflowType: String,
  status: String,
  startTime: Option[Long],
  closeTime: Option[Long],
  memo: Map[String, Seq[String]],
  searchAttributes: Map[String, Seq[String]]
)

case class QueryResult(
  executions: Seq[WorkflowExecutionInfo],
  hasMoreResults: Boolean
)

/**
 * Temporal client class for reusable connections
 *
 * @param uri Temporal server URI (e.g., "temporal://localhost:7233?namespace=default")
 */
class Temporal(uri: String)(implicit ec: ExecutionContext) {
  private val log = Logger(getClass.getName)

  private val t = TemporalURI(uri)

  private val serviceOptions = {
    val builder = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
      .setTarget(t.target)
      .setEnableKeepAlive(t.enableKeepAlive)
      .setKeepAliveTime(java.time.Duration.ofMillis(t.keepAliveTime))
      .setKeepAliveTimeout(java.time.Duration.ofMillis(t.keepAliveTimeout))
      .setRpcTimeout(java.time.Duration.ofMillis(t.rpcTimeout))

    // Add JWT authentication if auth token is present
    t.auth.foreach { token =>
      log.info(s"Configuring JWT authentication for WorkflowServiceStubs")
      val tokenSupplier = new io.temporal.authorization.AuthorizationTokenSupplier {
        override def supply(): String = s"Bearer $token"
      }
      builder.addGrpcMetadataProvider(new io.temporal.authorization.AuthorizationGrpcMetadataProvider(tokenSupplier))
    }

    builder.build()
  }

  private val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

  private val operatorServiceOptions = {
    val builder = io.temporal.serviceclient.OperatorServiceStubsOptions.newBuilder()
      .setTarget(t.target)
      .setEnableKeepAlive(t.enableKeepAlive)
      .setKeepAliveTime(java.time.Duration.ofMillis(t.keepAliveTime))
      .setKeepAliveTimeout(java.time.Duration.ofMillis(t.keepAliveTimeout))
      .setRpcTimeout(java.time.Duration.ofMillis(t.rpcTimeout))
      .setMetricsScope(new com.uber.m3.tally.NoopScope())
      .setHeaders(new io.grpc.Metadata())

    // Add JWT authentication if auth token is present
    t.auth.foreach { token =>
      log.info(s"Configuring JWT authentication for OperatorServiceStubs")
      val tokenSupplier = new io.temporal.authorization.AuthorizationTokenSupplier {
        override def supply(): String = s"Bearer $token"
      }
      builder.addGrpcMetadataProvider(new io.temporal.authorization.AuthorizationGrpcMetadataProvider(tokenSupplier))
    }

    builder.build()
  }

  private val operatorService = io.temporal.serviceclient.OperatorServiceStubs.newServiceStubs(operatorServiceOptions)

  private val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
    .setNamespace(t.namespace)
    .setDataConverter(ScalaDataConverter.create())
    .build()

  log.info(s"Connecting -> ${t.target} (namespace=${t.namespace})")
  private val client = WorkflowClient.newInstance(service, clientOptions)
  
  /**
   * Query workflows from Temporal server
   *
   * @param query Search query (e.g., "WorkflowId = 'por-workflow-*'" or "ExecutionStatus = 'Running'")
   * @param pageSize Number of results per page (default: 10)
   * @return QueryResult with workflow execution information
   */
  def query(query: String = "", pageSize: Int = 10): Future[QueryResult] = Future {
    // Build list request
    val requestBuilder = ListWorkflowExecutionsRequest.newBuilder()
      .setNamespace(t.namespace)
      .setPageSize(pageSize)

    if (query.nonEmpty) {
      requestBuilder.setQuery(query)
    }

    val request = requestBuilder.build()

    log.info(s"Querying workflows: query='$query', pageSize=$pageSize")

    // Execute query
    val response = service.blockingStub().listWorkflowExecutions(request)
    val executions = response.getExecutionsList.asScala

    // Convert to structured data
    val workflowInfos = executions.map { exec =>
      val workflowId = exec.getExecution.getWorkflowId
      val runId = exec.getExecution.getRunId
      val workflowType = exec.getType.getName
      val status = exec.getStatus.name()
      val startTime = if (exec.hasStartTime) {
        Some(exec.getStartTime.getSeconds * 1000)
      } else {
        None
      }
      val closeTime = if (exec.hasCloseTime) {
        Some(exec.getCloseTime.getSeconds * 1000)
      } else {
        None
      }

      // Extract memo
      val memo = if (exec.hasMemo && exec.getMemo.getFieldsMap.size() > 0) {
        exec.getMemo.getFieldsMap.asScala.map { case (key, _) =>
          key -> Seq(key) // Simplified: just return keys for now
        }.toMap
      } else {
        Map.empty[String, Seq[String]]
      }

      // Extract search attributes
      val searchAttributes = if (exec.hasSearchAttributes && exec.getSearchAttributes.getIndexedFieldsMap.size() > 0) {
        exec.getSearchAttributes.getIndexedFieldsMap.asScala.map { case (key, _) =>
          key -> Seq(key) // Simplified: just return keys for now
        }.toMap
      } else {
        Map.empty[String, Seq[String]]
      }

      WorkflowExecutionInfo(
        workflowId = workflowId,
        runId = runId,
        workflowType = workflowType,
        status = status,
        startTime = startTime,
        closeTime = closeTime,
        memo = memo,
        searchAttributes = searchAttributes
      )
    }.toSeq

    val hasMoreResults = !response.getNextPageToken.isEmpty

    QueryResult(workflowInfos, hasMoreResults)
  }

  /**
   * Get detailed information about a specific workflow by ID
   */
  def describe(workflowId: String, runId: Option[String] = None): Future[WorkflowExecutionInfo] = Future {
    // Get workflow stub
    val stub = runId match {
      case Some(rid) =>
        client.newUntypedWorkflowStub(workflowId, java.util.Optional.of(rid), java.util.Optional.empty())
      case None =>
        client.newUntypedWorkflowStub(workflowId, java.util.Optional.empty(), java.util.Optional.empty())
    }

    // Get workflow description
    val description = stub.describe()

    val wfId = description.getExecution.getWorkflowId
    val wfRunId = description.getExecution.getRunId
    val wfType = description.getWorkflowType
    val wfStatus = description.getStatus.name()

    val startTime = Option(description.getStartTime).map(_.toEpochMilli)
    val closeTime = Option(description.getCloseTime).map(_.toEpochMilli)

    // Search attributes
    val searchAttrs = Option(description.getSearchAttributes)
      .filter(!_.isEmpty)
      .map { attrs =>
        attrs.asScala.map { case (key, values) =>
          key -> values.asScala.toSeq.map(_.toString)
        }.toMap
      }.getOrElse(Map.empty[String, Seq[String]])

    WorkflowExecutionInfo(
      workflowId = wfId,
      runId = wfRunId,
      workflowType = wfType,
      status = wfStatus,
      startTime = startTime,
      closeTime = closeTime,
      memo = Map.empty, // Memo not available in describe()
      searchAttributes = searchAttrs
    )
  }

  /**
   * List workflows with optional filters
   */
  def list(status: Option[String] = None, workflowType: Option[String] = None, pageSize: Int = 10): Future[QueryResult] = {
    val queryParts = scala.collection.mutable.ArrayBuffer[String]()

    status.foreach { s =>
      queryParts += s"ExecutionStatus = '$s'"
    }

    workflowType.foreach { wt =>
      queryParts += s"WorkflowType = '$wt'"
    }

    val queryStr = queryParts.mkString(" AND ")

    this.query(queryStr, pageSize)
  }

  /**
   * Get workflow by run ID
   *
   * @param runId The run ID to search for
   * @return WorkflowExecutionInfo if found, or failure if not found or error
   */
  def get(runId: String): Future[WorkflowExecutionInfo] = {
    val queryStr = s"RunId = '$runId'"

    query(queryStr, pageSize = 1).map { result =>
      result.executions.headOption.getOrElse(
        throw new NoSuchElementException(s"Workflow with RunId '$runId' not found")
      )
    }
  }

  /**
   * Send signal to workflow
   *
   * @param workflowId Workflow ID
   * @param runId Optional run ID (if not provided, signals latest run)
   * @param signalName Signal name (e.g., "signalPol")
   * @param data Signal data as JsObject
   * @return Success message or error
   */
  def signal(workflowId: String, runId: Option[String], signalName: String, data: spray.json.JsObject): Future[String] = Future {
    log.info(s"Sending signal to workflow: workflowId=$workflowId, runId=$runId, signal=$signalName")

    // Get workflow stub
    val workflowStub = client.newWorkflowStub(classOf[io.syspulse.skel.wf.temporal.por.PorWorkflow],
      workflowId,
      runId.map(java.util.Optional.of(_)).getOrElse(java.util.Optional.empty()))

    // Send signal based on signal name
    signalName match {
      case "signalPol" =>
        // Parse and validate signal data
        import io.syspulse.skel.wf.temporal.por.{PolSignalValidator, PolFileData}

        val polFileData = PolSignalValidator.validateAndParse(data, workflowId) match {
          case Some(validData) =>
            validData
          case None =>
            throw new IllegalArgumentException(s"Invalid PoL signal data: failed validation")
        }

        workflowStub.signalPol(polFileData)
        s"Signal '$signalName' sent to workflow $workflowId${runId.map(r => s" (run $r)").getOrElse("")} with ${polFileData.liabilities.size} liabilities"

      case _ =>
        throw new IllegalArgumentException(s"Unknown signal name: $signalName")
    }
  }

  /**
   * Send signal to workflow by run ID (looks up workflow ID first)
   *
   * @param runId Run ID
   * @param signalName Signal name
   * @param data Signal data
   * @return Success message or error
   */
  def signalByRunId(runId: String, signalName: String, data: spray.json.JsObject): Future[String] = {
    // First, get workflow info by run ID to find workflow ID
    get(runId).flatMap { info =>
      signal(info.workflowId, Some(runId), signalName, data)
    }
  }

  /**
   * Query workflows by tenant ID
   *
   * @param tid Tenant ID
   * @param pageSize Number of results per page
   * @return QueryResult with workflow execution information
   */
  def queryByTenant(tid: Int, pageSize: Int = 100): Future[QueryResult] = {
    query(s"tid = $tid", pageSize)
  }

  /**
   * Query workflows by project ID
   *
   * @param pid Project ID
   * @param pageSize Number of results per page
   * @return QueryResult with workflow execution information
   */
  def queryByProject(pid: Int, pageSize: Int = 100): Future[QueryResult] = {
    query(s"pid = $pid", pageSize)
  }

  /**
   * Query workflows by system name
   *
   * @param sys System/project name
   * @param pageSize Number of results per page
   * @return QueryResult with workflow execution information
   */
  def queryBySystem(sys: String, pageSize: Int = 100): Future[QueryResult] = {
    query(s"sys = '$sys'", pageSize)
  }

  /**
   * Query workflows by tenant and project
   *
   * @param tid Tenant ID
   * @param pid Project ID
   * @param pageSize Number of results per page
   * @return QueryResult with workflow execution information
   */
  def queryByTenantAndProject(tid: Int, pid: Int, pageSize: Int = 100): Future[QueryResult] = {
    query(s"tid = $tid AND pid = $pid", pageSize)
  }

  /**
   * Query running workflows by tenant
   *
   * @param tid Tenant ID
   * @param pageSize Number of results per page
   * @return QueryResult with workflow execution information
   */
  def queryRunningByTenant(tid: Int, pageSize: Int = 100): Future[QueryResult] = {
    query(s"tid = $tid AND ExecutionStatus = 'Running'", pageSize)
  }

  /**
   * Query failed workflows by tenant and project
   *
   * @param tid Tenant ID
   * @param pid Project ID
   * @param pageSize Number of results per page
   * @return QueryResult with workflow execution information
   */
  def queryFailedByTenantAndProject(tid: Int, pid: Int, pageSize: Int = 100): Future[QueryResult] = {
    query(s"tid = $tid AND pid = $pid AND ExecutionStatus = 'Failed'", pageSize)
  }

  /**
   * Register a search attribute in Temporal namespace
   *
   * @param name Search attribute name (e.g., "tid", "pid", "sys")
   * @param attributeType Search attribute type (e.g., "Int", "Long", "Keyword", "Text", "Bool", "Datetime", "Double", "KeywordList")
   * @return Success message or error
   */
  def registerSearchAttribute(name: String, attributeType: String): Future[String] = Future {
    log.info(s"Registering search attribute: $name ($attributeType) in namespace ${t.namespace}")

    // Validate and map string type to IndexedValueType
    val indexedType = Temporal.validateSearchAttributeType(attributeType)

    // Create search attributes map
    val searchAttributes = Map(name -> indexedType).asJava

    // Build request
    val request = AddSearchAttributesRequest.newBuilder()
      .setNamespace(t.namespace)
      .putAllSearchAttributes(searchAttributes)
      .build()

    try {
      operatorService.blockingStub().addSearchAttributes(request)
      log.info(s"Successfully registered search attribute: $name ($attributeType)")
      s"Search attribute '$name' ($attributeType) registered successfully in namespace ${t.namespace}"
    } catch {
      case e: io.grpc.StatusRuntimeException if e.getStatus.getCode == io.grpc.Status.Code.ALREADY_EXISTS =>
        log.warn(s"Search attribute '$name' already exists in namespace ${t.namespace}")
        s"Search attribute '$name' already exists (skipped)"
      case e: io.grpc.StatusRuntimeException if e.getMessage.contains("cannot have more than") =>
        log.warn(s"Search attribute limit reached for type $attributeType: ${e.getMessage}")
        s"Search attribute '$name' limit reached (${e.getMessage})"
      case e: Exception =>
        log.error(s"Failed to register search attribute '$name': ${e.getMessage}", e)
        throw new RuntimeException(s"Failed to register search attribute '$name': ${e.getMessage}", e)
    }
  }

  /**
   * Register multiple search attributes
   *
   * @param attributes Map of attribute name -> type
   * @return Success messages for each attribute
   */
  def registerSearchAttributes(attributes: Map[String, String]): Future[Seq[String]] = {
    Future.sequence(
      attributes.map { case (name, attrType) =>
        registerSearchAttribute(name, attrType)
      }.toSeq
    )
  }

  /**
   * Shutdown the Temporal connection
   */
  def shutdown(): Unit = {
    log.debug(s"Shutdown: ${t.target}")
    operatorService.shutdown()
    service.shutdown()
  }
}

/**
 * Companion object with static methods for backward compatibility
 */
object Temporal {
  private val log = Logger(getClass.getName)

  /**
   * Validate search attribute type (for testing and validation)
   *
   * @param attributeType Search attribute type string
   * @return IndexedValueType if valid
   * @throws IllegalArgumentException if invalid
   */
  def validateSearchAttributeType(attributeType: String): IndexedValueType = {
    import io.temporal.api.enums.v1.IndexedValueType

    attributeType.toLowerCase match {
      case "int" | "long" => IndexedValueType.INDEXED_VALUE_TYPE_INT
      case "keyword" => IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD
      case "text" => IndexedValueType.INDEXED_VALUE_TYPE_TEXT
      case "bool" | "boolean" => IndexedValueType.INDEXED_VALUE_TYPE_BOOL
      case "datetime" | "timestamp" => IndexedValueType.INDEXED_VALUE_TYPE_DATETIME
      case "double" => IndexedValueType.INDEXED_VALUE_TYPE_DOUBLE
      case "keywordlist" => IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD_LIST
      case _ =>
        throw new IllegalArgumentException(
          s"Unknown attribute type: $attributeType. Valid types: Int, Long, Keyword, Text, Bool, Datetime, Double, KeywordList"
        )
    }
  }

  /**
   * Query workflows from Temporal server (static method)
   *
   * @param uri Temporal server URI
   * @param query Search query (e.g., "WorkflowId = 'por-workflow-*'" or "ExecutionStatus = 'Running'")
   * @param pageSize Number of results per page (default: 10)
   * @return QueryResult with workflow execution information
   */
  def query(uri: String, query: String = "", pageSize: Int = 10)(implicit ec: ExecutionContext): Future[QueryResult] = {
    val temporal = new Temporal(uri)
    temporal.query(query, pageSize).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Query workflows by tenant ID (static method)
   */
  def queryByTenant(uri: String, tid: Int, pageSize: Int = 100)(implicit ec: ExecutionContext): Future[QueryResult] = {
    val temporal = new Temporal(uri)
    temporal.queryByTenant(tid, pageSize).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Query workflows by project ID (static method)
   */
  def queryByProject(uri: String, pid: Int, pageSize: Int = 100)(implicit ec: ExecutionContext): Future[QueryResult] = {
    val temporal = new Temporal(uri)
    temporal.queryByProject(pid, pageSize).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Query workflows by system name (static method)
   */
  def queryBySystem(uri: String, sys: String, pageSize: Int = 100)(implicit ec: ExecutionContext): Future[QueryResult] = {
    val temporal = new Temporal(uri)
    temporal.queryBySystem(sys, pageSize).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Register a search attribute (static method)
   *
   * @param uri Temporal server URI
   * @param name Search attribute name
   * @param attributeType Search attribute type (Int, Long, Keyword, Text, Bool, Datetime, Double, KeywordList)
   */
  def registerSearchAttribute(uri: String, name: String, attributeType: String)(implicit ec: ExecutionContext): Future[String] = {
    val temporal = new Temporal(uri)
    temporal.registerSearchAttribute(name, attributeType).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Register multiple search attributes (static method)
   *
   * @param uri Temporal server URI
   * @param attributes Map of attribute name -> type
   */
  def registerSearchAttributes(uri: String, attributes: Map[String, String])(implicit ec: ExecutionContext): Future[Seq[String]] = {
    val temporal = new Temporal(uri)
    temporal.registerSearchAttributes(attributes).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Get detailed information about a specific workflow by ID (static method)
   */
  def describe(uri: String, workflowId: String, runId: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowExecutionInfo] = {
    val temporal = new Temporal(uri)
    temporal.describe(workflowId, runId).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * List workflows with optional filters (static method)
   */
  def list(uri: String, status: Option[String] = None, workflowType: Option[String] = None, pageSize: Int = 10)(implicit ec: ExecutionContext): Future[QueryResult] = {
    val temporal = new Temporal(uri)
    temporal.list(status, workflowType, pageSize).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Get workflow by run ID (static method)
   *
   * @param uri Temporal server URI
   * @param runId The run ID to search for
   * @return WorkflowExecutionInfo if found, or failure if not found or error
   */
  def get(uri: String, runId: String)(implicit ec: ExecutionContext): Future[WorkflowExecutionInfo] = {
    val temporal = new Temporal(uri)
    temporal.get(runId).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Send signal to workflow (static method)
   *
   * @param uri Temporal server URI
   * @param workflowId Workflow ID
   * @param runId Optional run ID
   * @param signalName Signal name
   * @param data Signal data
   */
  def signal(uri: String, workflowId: String, runId: Option[String], signalName: String, data: spray.json.JsObject)(implicit ec: ExecutionContext): Future[String] = {
    val temporal = new Temporal(uri)
    temporal.signal(workflowId, runId, signalName, data).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Send signal to workflow by run ID (static method)
   *
   * @param uri Temporal server URI
   * @param runId Run ID
   * @param signalName Signal name
   * @param data Signal data
   */
  def signalByRunId(uri: String, runId: String, signalName: String, data: spray.json.JsObject)(implicit ec: ExecutionContext): Future[String] = {
    val temporal = new Temporal(uri)
    temporal.signalByRunId(runId, signalName, data).andThen { case _ =>
      temporal.shutdown()
    }
  }

  /**
   * Create a new Temporal instance
   */
  def apply(uri: String)(implicit ec: ExecutionContext): Temporal = new Temporal(uri)
}
