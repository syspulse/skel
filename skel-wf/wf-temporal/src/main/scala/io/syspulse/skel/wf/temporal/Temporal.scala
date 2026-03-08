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

object Temporal {
  private val log = Logger(getClass.getName)

  /**
   * Query workflows from Temporal server
   *
   * @param uri Temporal server URI
   * @param query Search query (e.g., "WorkflowId = 'por-workflow-*'" or "ExecutionStatus = 'Running'")
   * @param pageSize Number of results per page (default: 10)
   * @return QueryResult with workflow execution information
   */
  def query(uri: String, query: String = "", pageSize: Int = 10): Try[QueryResult] = {
    try {
      val t = TemporalURI(uri)
      log.info(s"Connecting to Temporal at ${t.target} namespace=${t.namespace}")

      val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
        .setTarget(t.target)
        .setEnableKeepAlive(t.enableKeepAlive)
        .setKeepAliveTime(java.time.Duration.ofSeconds(t.keepAliveTimeSec))
        .setKeepAliveTimeout(java.time.Duration.ofSeconds(t.keepAliveTimeoutSec))
        .setRpcTimeout(java.time.Duration.ofSeconds(t.rpcTimeoutSec))
        .build()

      val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

      val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
        .setNamespace(t.namespace)
        .setDataConverter(ScalaDataConverter.create())
        .build()

      val client = WorkflowClient.newInstance(service, clientOptions)

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

      service.shutdown()
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
  def describe(uri: String, workflowId: String, runId: Option[String] = None): Try[WorkflowExecutionInfo] = {
    try {
      val t = TemporalURI(uri)
      log.info(s"Connecting to Temporal at ${t.target} namespace=${t.namespace}")

      val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
        .setTarget(t.target)
        .setEnableKeepAlive(t.enableKeepAlive)
        .setKeepAliveTime(java.time.Duration.ofSeconds(t.keepAliveTimeSec))
        .setKeepAliveTimeout(java.time.Duration.ofSeconds(t.keepAliveTimeoutSec))
        .setRpcTimeout(java.time.Duration.ofSeconds(t.rpcTimeoutSec))
        .build()

      val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

      val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
        .setNamespace(t.namespace)
        .setDataConverter(ScalaDataConverter.create())
        .build()

      val client = WorkflowClient.newInstance(service, clientOptions)

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

      service.shutdown()
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
  def list(uri: String, status: Option[String] = None, workflowType: Option[String] = None, pageSize: Int = 10): Try[QueryResult] = {
    val queryParts = scala.collection.mutable.ArrayBuffer[String]()

    status.foreach { s =>
      queryParts += s"ExecutionStatus = '$s'"
    }

    workflowType.foreach { wt =>
      queryParts += s"WorkflowType = '$wt'"
    }

    val query = queryParts.mkString(" AND ")

    this.query(uri, query, pageSize)
  }
}
