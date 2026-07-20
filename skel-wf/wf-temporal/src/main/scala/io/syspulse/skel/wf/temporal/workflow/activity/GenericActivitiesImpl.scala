package io.syspulse.skel.wf.temporal.workflow.activity

import com.typesafe.scalalogging.Logger
import spray.json._

import io.hacken.ext.wf.{WorkflowRun, WorkflowSchema}
import io.hacken.ext.detector.{DetectorConfig, DetectorConfigJson}
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}

import scala.util.{Success, Failure, Try}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Generic Activities Implementation
 *
 * This is a truly generic implementation that handles any activity by
 * executing it as a pass-through with generic output.
 *
 * For domain-specific workflows (e.g., PoR), extend this class and
 * override executeActivity to provide custom implementations.
 */
class GenericActivitiesImpl(
  val schemaStore: WorkflowSchemaStore,
  val runStore: WorkflowRunStore,
  val configStore: WorkflowConfigStore
) extends GenericActivities {

  protected val log = Logger(getClass)

  override def loadExecutionContext(schemaId: Int, stepConfigIds: Seq[Int]): io.syspulse.skel.wf.temporal.workflow.WorkflowExecutionContext = {
    log.info(s"Loading execution context: schemaId=${schemaId}, stepConfigIds=${stepConfigIds.mkString(",")}")

    // Load schema
    val schema = GenericActivities.getWorkflowSchema(schemaStore, schemaId)
    log.info(s"Loaded schema: ${schema.name}")

    // Load step metadata for all steps
    val stepMetadata = stepConfigIds.map { configId =>
      log.info(s"Loading metadata for configId=${configId}")
      val name = GenericActivities.getDetectorConfigName(configStore, configId)
      val stepType = GenericActivities.getStepType(configStore, configId)
      log.info(s"  configId=${configId}, name=${name}, stepType=${stepType}")
      configId -> io.syspulse.skel.wf.temporal.workflow.StepMetadata(configId, name, stepType)
    }.toMap

    log.info(s"Loaded execution context with ${stepMetadata.size} steps: keys=${stepMetadata.keys.mkString(",")}")
    val context = io.syspulse.skel.wf.temporal.workflow.WorkflowExecutionContext(schema, stepMetadata)
    log.info(s"Returning context with stepMetadata map containing: ${context.stepMetadata.keys.mkString(",")}")
    context
  }

  override def executeActivity(run: WorkflowRun): ActivityResult = {
    val configId = run.cursor
    log.info(s"Executing generic activity for config: ${configId}")

    // Fetch config from store
    val config = configStore.???(configId) match {
      case Success(c) => c
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }

    log.info(s"Executing activity: ${config.name}")

    // Generic failure injection: any step whose config has "fail": true throws here
    failIfConfigured(config)

    try {
      // Execute as generic activity (pass-through with generic output)
      val result = executeGenericActivity(config)

      // Store updated config
      Try(Await.result(configStore.+(result), 10.seconds)) match {
        case Success(updated) =>
          log.info(s"Activity ${config.name} completed successfully")

          // Extract output JsObject if present
          val output = updated.config
            .flatMap(_.asJsObject.fields.get("output"))
            .collect { case obj: JsObject => obj }
            .getOrElse(JsObject())

          // Build ActivityResult
          ActivityResult.success(updated.id, updated.name, output)
        case Failure(e) =>
          log.error(s"Failed to store config: ${e.getMessage}")
          throw e
      }

    } catch {
      case e: Exception =>
        log.error(s"Activity ${config.name} failed: ${e.getMessage}", e)
        throw e
    }
  }

  /**
   * Generic failure injection.
   *
   * Any step can be forced to fail by setting these keys in its DetectorConfig.config:
   *   - "fail": true                (required to trigger the failure)
   *   - "failMessage": "..."        (optional custom error message)
   *
   * Throws a non-retryable Temporal ApplicationFailure so the activity is marked Failed
   * immediately (no retries) and the whole Workflow FAILs.
   */
  protected def failIfConfigured(config: DetectorConfig): Unit = {
    if (DetectorConfig.getBoolean(config, "fail", false)) {
      val msg = DetectorConfig.getString(config, "failMessage",
        s"Step '${config.name}' (id=${config.id}) forced failure")
      log.error(s"Forced failure: ${msg}")
      throw io.temporal.failure.ApplicationFailure.newNonRetryableFailure(msg, "StepForcedFailure")
    }
  }

  /**
   * Execute generic activity
   *
   * This is a pass-through implementation that adds generic output metadata.
   * Domain-specific implementations should override executeActivity to provide custom logic.
   */
  protected def executeGenericActivity(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing generic activity: ${config.name}")

    val output = JsObject(
      "activity" -> JsString(config.name),
      "executed" -> JsBoolean(true),
      "timestamp" -> JsNumber(System.currentTimeMillis())
    )

    addOutput(config, output)
  }

  /**
   * Add output to DetectorConfig
   *
   * Helper method for subclasses to add output data to config
   */
  protected def addOutput(config: DetectorConfig, output: JsObject): DetectorConfig = {
    val currentConfig = config.config.getOrElse(JsObject())
    val updatedConfig = JsObject(
      currentConfig.fields + ("output" -> output)
    )

    config.copy(config = Some(updatedConfig))
  }
}
