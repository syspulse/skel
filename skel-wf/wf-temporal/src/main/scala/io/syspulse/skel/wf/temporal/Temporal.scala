package io.syspulse.skel.wf.temporal

import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._

import io.temporal.client.{WorkflowClient, WorkflowStub}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest
import io.temporal.api.enums.v1.WorkflowExecutionStatus
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
class Temporal(uri: String) {
  private val log = Logger(getClass.getName)

  private val t = TemporalURI(uri)

  private val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
    .setTarget(t.target)
    .setEnableKeepAlive(t.enableKeepAlive)
    .setKeepAliveTime(java.time.Duration.ofMillis(t.keepAliveTime))
    .setKeepAliveTimeout(java.time.Duration.ofMillis(t.keepAliveTimeout))
    .setRpcTimeout(java.time.Duration.ofMillis(t.rpcTimeout))
    .build()

  private val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

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
  def query(query: String = "", pageSize: Int = 10): Try[QueryResult] = {
    try {
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

      Success(QueryResult(workflowInfos, hasMoreResults))

    } catch {
      case e: Exception =>
        log.error(s"Failed to query workflows: ${e.getMessage}", e)
        Failure(e)
    }
  }

  /**
   * Get detailed information about a specific workflow by ID
   */
  def describe(workflowId: String, runId: Option[String] = None): Try[WorkflowExecutionInfo] = {
    try {
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

      val info = WorkflowExecutionInfo(
        workflowId = wfId,
        runId = wfRunId,
        workflowType = wfType,
        status = wfStatus,
        startTime = startTime,
        closeTime = closeTime,
        memo = Map.empty, // Memo not available in describe()
        searchAttributes = searchAttrs
      )

      Success(info)

    } catch {
      case e: Exception =>
        log.error(s"Failed to describe workflow $workflowId: ${e.getMessage}", e)
        Failure(e)
    }
  }

  /**
   * List workflows with optional filters
   */
  def list(status: Option[String] = None, workflowType: Option[String] = None, pageSize: Int = 10): Try[QueryResult] = {
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
  def get(runId: String): Try[WorkflowExecutionInfo] = {
    val queryStr = s"RunId = '$runId'"

    query(queryStr, pageSize = 1).flatMap { result =>
      result.executions.headOption match {
        case Some(info) => Success(info)
        case None => Failure(new NoSuchElementException(s"Workflow with RunId '$runId' not found"))
      }
    }
  }

  /**
   * Shutdown the Temporal connection
   */
  def shutdown(): Unit = {
    log.debug(s"Shutdown: ${t.target}")
    service.shutdown()
  }
}

/**
 * Companion object with static methods for backward compatibility
 */
object Temporal {
  private val log = Logger(getClass.getName)

  /**
   * Query workflows from Temporal server (static method)
   *
   * @param uri Temporal server URI
   * @param query Search query (e.g., "WorkflowId = 'por-workflow-*'" or "ExecutionStatus = 'Running'")
   * @param pageSize Number of results per page (default: 10)
   * @return QueryResult with workflow execution information
   */
  def query(uri: String, query: String = "", pageSize: Int = 10): Try[QueryResult] = {
    val temporal = new Temporal(uri)
    try {
      temporal.query(query, pageSize)
    } finally {
      temporal.shutdown()
    }
  }

  /**
   * Get detailed information about a specific workflow by ID (static method)
   */
  def describe(uri: String, workflowId: String, runId: Option[String] = None): Try[WorkflowExecutionInfo] = {
    val temporal = new Temporal(uri)
    try {
      temporal.describe(workflowId, runId)
    } finally {
      temporal.shutdown()
    }
  }

  /**
   * List workflows with optional filters (static method)
   */
  def list(uri: String, status: Option[String] = None, workflowType: Option[String] = None, pageSize: Int = 10): Try[QueryResult] = {
    val temporal = new Temporal(uri)
    try {
      temporal.list(status, workflowType, pageSize)
    } finally {
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
  def get(uri: String, runId: String): Try[WorkflowExecutionInfo] = {
    val temporal = new Temporal(uri)
    try {
      temporal.get(runId)
    } finally {
      temporal.shutdown()
    }
  }

  /**
   * Create a new Temporal instance
   */
  def apply(uri: String): Temporal = new Temporal(uri)
}
