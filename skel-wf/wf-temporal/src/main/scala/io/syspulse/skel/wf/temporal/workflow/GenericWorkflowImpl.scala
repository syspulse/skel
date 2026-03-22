package io.syspulse.skel.wf.temporal.workflow

import com.typesafe.scalalogging.Logger
import io.temporal.workflow.Workflow
import io.temporal.activity.ActivityOptions
import java.time.Duration

import io.hacken.ext.wf.{WorkflowRun, WorkflowSchema}
import io.hacken.ext.detector.DetectorConfig
import io.syspulse.skel.wf.temporal.workflow.activity.GenericActivities

/**
 * Generic Workflow Implementation
 *
 * Executes multi-step workflows with cursor-based step tracking
 * Supports WAIT (manual) and AUTO (automatic) step types
 */
class GenericWorkflowImpl extends GenericWorkflow {

  private val log = Logger(getClass.getName)

  // Activity options for business activities (visible in UI)
  private val activityOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(30))
    .setScheduleToCloseTimeout(Duration.ofHours(2))
    .build()

  // Local activity options for infrastructure activities (hidden from UI)
  private val localActivityOptions = io.temporal.activity.LocalActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(10))
    .build()

  // Regular activities for business operations (visible in Temporal UI)
  private val activities = Workflow.newActivityStub(classOf[GenericActivities], activityOptions)

  // Local activities for infrastructure operations (NOT visible in Temporal UI)
  private val localActivities = Workflow.newLocalActivityStub(classOf[GenericActivities], localActivityOptions)

  // Workflow state (survives worker restarts - managed by Temporal)
  @volatile
  private var currentRun: WorkflowRun = _

  @volatile
  private var continueSignal: Boolean = false

  @volatile
  private var expectedConfigId: Int = -1

  override def continueWorkflow(configId: Int): Unit = {
    val info = Workflow.getInfo()
    val wid = s"[${info.getWorkflowId} / ${info.getRunId}]"
    log.info(s"${wid} Received continue signal for configId=${configId}, cursor=${currentRun.cursor}")

    // Only continue if configId matches cursor
    if (currentRun.cursor == configId && expectedConfigId == configId) {
      log.info(s"${wid} Continue signal accepted")
      continueSignal = true
    } else {
      log.warn(s"${wid} Continue signal ignored: configId=${configId}, cursor=${currentRun.cursor}, expected=${expectedConfigId}")
    }
  }

  override def getWorkflowRun(): WorkflowRun = currentRun

  override def getStatus(): String = if (currentRun != null) currentRun.status else "UNKNOWN"

  override def getCursor(): Int = if (currentRun != null) currentRun.cursor else -1

  override def execute(run: WorkflowRun): WorkflowRun = {
    val info = Workflow.getInfo()
    implicit val wid = s"[${info.getWorkflowId} / ${info.getRunId}]"

    log.info(s"${wid} Starting Generic Workflow: schema=${run.schema}, steps=${run.steps}")

    // Initialize current run
    currentRun = run.copy(
      wid = info.getWorkflowId,
      rid = Some(info.getRunId),
      status = "RUNNING",
      cursor = -1
    )

    try {
      // Get workflow schema (using local activity - hidden from UI)
      val schema = localActivities.getWorkflowSchema(run.schema)
      log.info(s"${wid} Loaded schema: ${schema.name} v${schema.version}")

      // Execute steps based on connections
      currentRun = executeStepsInOrder(currentRun, schema)

      // Mark as finished
      currentRun = currentRun.copy(status = "FINISHED")
      log.info(s"${wid} Workflow finished successfully")

    } catch {
      case e: Exception =>
        log.error(s"${wid} Workflow failed: ${e.getMessage}", e)
        currentRun = currentRun.copy(status = "FAILED")
    }

    currentRun
  }

  /**
   * Execute workflow steps in order based on connections
   */
  private def executeStepsInOrder(run: WorkflowRun, schema: WorkflowSchema)(implicit wid: String): WorkflowRun = {
    // Build connection map: node_id -> next_node_id
    val connectionMap = schema.connections.map(c => c.from -> c.to).toMap

    // Find starting node (node with no incoming connection)
    val allTargets = schema.connections.map(_.to).toSet
    val startingNodes = schema.nodes.filterNot(n => allTargets.contains(n.id))

    if (startingNodes.isEmpty) {
      log.error(s"${wid} No starting node found in workflow")
      currentRun = currentRun.copy(status = "FAILED")
      return currentRun
    }

    // Start from first node
    var currentNodeId: Option[Int] = Some(startingNodes.head.id)
    val nodeMap = schema.nodes.map(n => n.id -> n).toMap

    while (currentNodeId.isDefined) {
      val nodeId = currentNodeId.get
      val node = nodeMap.get(nodeId)

      node match {
        case Some(n) =>
          // Find corresponding DetectorConfig
          val configIdOpt = findConfigForNode(currentRun, n)

          configIdOpt match {
            case Some(configId) =>
              log.info(s"${wid} Executing node ${n.id}: ${n.name} (configId=${configId})")

              // Execute step and update instance variable
              currentRun = executeStep(currentRun, configId)

              // Check if workflow was stopped or failed
              if (currentRun.status == "STOPPED" || currentRun.status == "FAILED") {
                return currentRun
              }

              // Move to next node
              currentNodeId = connectionMap.get(nodeId)

            case None =>
              log.error(s"${wid} No DetectorConfig found for node ${n.id}")
              currentRun = currentRun.copy(status = "FAILED")
              return currentRun
          }

        case None =>
          log.error(s"${wid} Node ${nodeId} not found in schema")
          currentRun = currentRun.copy(status = "FAILED")
          return currentRun
      }
    }

    currentRun
  }

  /**
   * Find DetectorConfig ID for given node
   */
  private def findConfigForNode(run: WorkflowRun, node: io.hacken.ext.wf.WorkflowSchemaNode): Option[Int] = {
    // For now, assume steps are in order matching nodes
    // TODO: Implement proper mapping based on node.sid (DetectorSchema ID)
    val nodeIndex = run.steps.indexOf(node.id)
    if (nodeIndex >= 0 && nodeIndex < run.steps.size) {
      Some(run.steps(nodeIndex))
    } else {
      None
    }
  }

  /**
   * Execute single step
   */
  private def executeStep(run: WorkflowRun, configId: Int)(implicit wid: String): WorkflowRun = {
    // Update cursor in instance variable immediately (for queries)
    currentRun = run.copy(cursor = configId)
    log.info(s"${wid} Step cursor set to ${configId}")

    // Verify config exists (using local activity - hidden from UI)
    val verifiedConfigId = localActivities.getDetectorConfig(configId)
    log.info(s"${wid} Verified config: ${verifiedConfigId}")

    // Get step type (using local activity - hidden from UI)
    val stepType = localActivities.getStepType(configId)
    log.info(s"${wid} Step type: ${stepType}")

    stepType match {
      case "WAIT" =>
        // Manual step - wait for signal
        log.info(s"${wid} Step is WAIT - pausing for user input")
        currentRun = currentRun.copy(status = "WAITING")

        // Store run state (using local activity - hidden from UI)
        localActivities.updateWorkflowRun(currentRun)

        // Wait for continue signal
        expectedConfigId = configId
        continueSignal = false

        log.info(s"${wid} Waiting for continue signal for configId=${configId}")
        Workflow.await(() => continueSignal)

        log.info(s"${wid} Continue signal received, resuming execution")
        currentRun = currentRun.copy(status = "RUNNING")

      case "AUTO" | _ =>
        // Automatic step - execute immediately
        log.info(s"${wid} Step is AUTO - executing activity")

        try {
          // Get business name for activity (using local activity - hidden from UI)
          val activityName = localActivities.getDetectorConfigName(configId)
          log.info(s"${wid} Executing business activity: ${activityName}")

          // Execute BUSINESS activity with business name (VISIBLE in Temporal UI)
          val untypedStub = Workflow.newUntypedActivityStub(activityOptions)
          val executedConfigId = untypedStub.execute(activityName, classOf[Int], Int.box(configId)).asInstanceOf[Int]
          log.info(s"${wid} Business activity '${activityName}' completed: ${executedConfigId}")

          // Update run with output
          currentRun = currentRun.copy(status = "RUNNING")

        } catch {
          case e: Exception =>
            log.error(s"${wid} Activity execution failed: ${e.getMessage}", e)
            currentRun = currentRun.copy(status = "FAILED")
        }
    }

    // Store updated run state (using local activity - hidden from UI)
    localActivities.updateWorkflowRun(currentRun)

    currentRun
  }
}
