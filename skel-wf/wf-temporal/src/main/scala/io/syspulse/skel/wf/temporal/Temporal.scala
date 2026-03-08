package io.syspulse.skel.wf.temporal

import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._

import io.temporal.client.{WorkflowClient, WorkflowStub}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest
import io.temporal.api.enums.v1.WorkflowExecutionStatus
import com.typesafe.scalalogging.Logger

object Temporal {
  private val log = Logger(getClass.getName)

  /**
   * Query workflows from Temporal server
   *
   * @param uri Temporal server URI
   * @param query Search query (e.g., "WorkflowId = 'por-workflow-*'" or "ExecutionStatus = 'Running'")
   * @param pageSize Number of results per page (default: 10)
   * @return Formatted string with workflow information
   */
  def query(uri: String, query: String = "", pageSize: Int = 10): Try[String] = {
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

      if (executions.isEmpty) {
        service.shutdown()
        return Success("No workflows found")
      }

      // Format results
      val result = new StringBuilder
      result.append(s"Found ${executions.size} workflow(s):\n\n")

      executions.zipWithIndex.foreach { case (exec, idx) =>
        val workflowId = exec.getExecution.getWorkflowId
        val runId = exec.getExecution.getRunId
        val workflowType = exec.getType.getName
        val status = exec.getStatus.name()
        val startTime = if (exec.hasStartTime) {
          new java.util.Date(exec.getStartTime.getSeconds * 1000).toString
        } else {
          "N/A"
        }
        val closeTime = if (exec.hasCloseTime) {
          new java.util.Date(exec.getCloseTime.getSeconds * 1000).toString
        } else {
          "N/A"
        }

        result.append(s"${idx + 1}. Workflow ID: $workflowId\n")
        result.append(s"   Run ID: $runId\n")
        result.append(s"   Type: $workflowType\n")
        result.append(s"   Status: $status\n")
        result.append(s"   Start Time: $startTime\n")
        result.append(s"   Close Time: $closeTime\n")

        // Show memo if present
        if (exec.hasMemo && exec.getMemo.getFieldsMap.size() > 0) {
          result.append(s"   Memo: ${exec.getMemo.getFieldsMap.asScala.keys.mkString(", ")}\n")
        }

        // Show search attributes if present
        if (exec.hasSearchAttributes && exec.getSearchAttributes.getIndexedFieldsMap.size() > 0) {
          result.append(s"   Search Attributes: ${exec.getSearchAttributes.getIndexedFieldsMap.asScala.keys.mkString(", ")}\n")
        }

        result.append("\n")
      }

      // Show next page token if available
      if (!response.getNextPageToken.isEmpty) {
        result.append("(More results available - use next page token)\n")
      }

      service.shutdown()
      Success(result.toString)

    } catch {
      case e: Exception =>
        log.error(s"Failed to query workflows: ${e.getMessage}", e)
        Failure(e)
    }
  }

  /**
   * Get detailed information about a specific workflow by ID
   */
  def describe(uri: String, workflowId: String, runId: Option[String] = None): Try[String] = {
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

      val result = new StringBuilder
      result.append(s"Workflow Details:\n\n")
      result.append(s"Workflow ID: ${description.getExecution.getWorkflowId}\n")
      result.append(s"Run ID: ${description.getExecution.getRunId}\n")
      result.append(s"Type: ${description.getWorkflowType}\n")
      result.append(s"Status: ${description.getStatus.name()}\n")

      val startTime = description.getStartTime
      if (startTime != null) {
        result.append(s"Start Time: ${java.util.Date.from(startTime)}\n")
      }

      val closeTime = description.getCloseTime
      if (closeTime != null) {
        result.append(s"Close Time: ${java.util.Date.from(closeTime)}\n")
      }

      // Search attributes
      val searchAttrs = description.getSearchAttributes
      if (searchAttrs != null && !searchAttrs.isEmpty) {
        result.append(s"\nSearch Attributes:\n")
        searchAttrs.asScala.foreach { case (key, values) =>
          result.append(s"  $key: ${values.asScala.mkString(", ")}\n")
        }
      }

      result.append("\n(Note: For full workflow details including memo, use 'temporal query' command)\n")

      service.shutdown()
      Success(result.toString)

    } catch {
      case e: Exception =>
        log.error(s"Failed to describe workflow $workflowId: ${e.getMessage}", e)
        Failure(e)
    }
  }

  /**
   * List workflows with optional filters
   */
  def list(uri: String, status: Option[String] = None, workflowType: Option[String] = None, pageSize: Int = 10): Try[String] = {
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
