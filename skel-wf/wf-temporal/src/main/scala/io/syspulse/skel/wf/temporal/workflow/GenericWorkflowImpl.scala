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
  private var executionContext: WorkflowExecutionContext = _

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

    log.info(s"${wid} Starting Generic Workflow: schema=${run.schema}, steps=${run.steps.size}")

    // Initialize current run
    currentRun = run.copy(
      wid = info.getWorkflowId,
      rid = Some(info.getRunId),
      status = "RUNNING",
      cursor = -1
    )

    try {
      // Build execution context from workflow steps
      val stepMetadataMap: Map[Int, StepMetadata] = run.steps.map { step =>
        log.info(s"${wid} Step ${step.id}: name=${step.name}, type=${step.typ}")
        step.id -> StepMetadata(step.id, step.name, step.typ)
      }.toMap

      log.info(s"${wid} Built ${stepMetadataMap.size} step metadata entries")

      // Create minimal schema (we don't actually need the full schema for sequential execution)
      val dummySchema = io.hacken.ext.wf.WorkflowSchema(
        id = run.schema,
        createdAt = 0L,
        updatedAt = 0L,
        status = "ACTIVE",
        name = "GenericWorkflow",
        version = "1.0",
        title = "Generic Workflow",
        description = "",
        author = "",
        icon = None,
        faq = None,
        tags = Seq(),
        nodes = Seq(),
        connections = Seq()
      )

      executionContext = WorkflowExecutionContext(dummySchema, stepMetadataMap)
      log.info(s"${wid} Built execution context with ${executionContext.stepMetadata.size} steps")

      // Execute steps in sequential order
      currentRun = executeStepsInOrder(currentRun, dummySchema)

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
   * Execute workflow steps in order
   *
   * Steps are executed in the order specified by run.steps array
   */
  private def executeStepsInOrder(run: WorkflowRun, schema: WorkflowSchema)(implicit wid: String): WorkflowRun = {
    log.info(s"${wid} Executing ${run.steps.size} steps in order")

    // Execute steps in order from the steps array
    for (step <- run.steps) {
      log.info(s"${wid} Executing step: configId=${step.id}, name=${step.name}")

      // Execute step and update instance variable
      currentRun = executeStep(currentRun, step.id)

      // Check if workflow was stopped or failed
      if (currentRun.status == "STOPPED" || currentRun.status == "FAILED") {
        log.warn(s"${wid} Workflow stopped at step ${step.id}: status=${currentRun.status}")
        return currentRun
      }
    }

    log.info(s"${wid} All steps executed successfully")
    currentRun
  }

  /**
   * Execute single step
   */
  private def executeStep(run: WorkflowRun, configId: Int)(implicit wid: String): WorkflowRun = {
    // Update cursor in instance variable immediately (for queries)
    currentRun = run.copy(cursor = configId)
    log.info(s"${wid} Step cursor set to ${configId}")

    // Debug: log execution context state
    log.info(s"${wid} ExecutionContext available keys: ${executionContext.stepMetadata.keys.mkString(",")}")
    log.info(s"${wid} Looking for configId=${configId}")

    // Get step metadata from execution context (deterministic - no I/O)
    val stepMeta = executionContext.stepMetadata.getOrElse(configId, {
      log.error(s"${wid} Step metadata not found for configId=${configId}")
      log.error(s"${wid} Available metadata keys: ${executionContext.stepMetadata.keys.mkString(",")}")
      throw new IllegalStateException(s"Step metadata not found for configId=${configId}. Available: ${executionContext.stepMetadata.keys.mkString(",")}")
    })

    log.info(s"${wid} Step: ${stepMeta.name}, type: ${stepMeta.stepType}")

    stepMeta.stepType match {
      case "WAIT" =>
        // Manual step - wait for signal
        log.info(s"${wid} Step '${stepMeta.name}' is WAIT - pausing for user input")
        currentRun = currentRun.copy(status = "WAITING")

        // Wait for continue signal
        expectedConfigId = configId
        continueSignal = false

        log.info(s"${wid} Waiting for continue signal for '${stepMeta.name}' (configId=${configId})")
        Workflow.await(() => continueSignal)

        log.info(s"${wid} Continue signal received for '${stepMeta.name}', resuming execution")
        currentRun = currentRun.copy(status = "RUNNING")

      case "AUTO" | _ =>
        // Automatic step - execute immediately
        log.info(s"${wid} Step '${stepMeta.name}' is AUTO - executing business activity")

        try {
          // Execute BUSINESS activity with business name (VISIBLE in Temporal UI)
          val untypedStub = Workflow.newUntypedActivityStub(activityOptions)
          val result = untypedStub.execute(
            stepMeta.name,
            classOf[io.syspulse.skel.wf.temporal.workflow.activity.ActivityResult],
            Int.box(configId)
          ).asInstanceOf[io.syspulse.skel.wf.temporal.workflow.activity.ActivityResult]

          log.info(s"${wid} Business activity '${stepMeta.name}' completed: " +
            s"configId=${result.configId}, status=${result.status}, output=${result.output}")

          // Update run with output
          currentRun = currentRun.copy(status = "RUNNING")

        } catch {
          case e: Exception =>
            log.error(s"${wid} Business activity '${stepMeta.name}' failed: ${e.getMessage}", e)
            currentRun = currentRun.copy(status = "FAILED")
        }
    }

    currentRun
  }
}
