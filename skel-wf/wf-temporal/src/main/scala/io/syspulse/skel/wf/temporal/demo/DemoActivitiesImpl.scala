package io.syspulse.skel.wf.temporal.demo

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.wf.temporal.workflow.activity.GenericActivitiesImpl
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}

/**
 * Demo Activities Implementation
 *
 * Implements GenericActivities for demo workflows
 * Delegates to DemoActivities for step execution
 */
class DemoActivitiesImpl(
  schemaStore: WorkflowSchemaStore,
  runStore: WorkflowRunStore,
  configStore: WorkflowConfigStore
) extends GenericActivitiesImpl(schemaStore, runStore, configStore) {

  override def executeActivity(run: io.hacken.ext.wf.WorkflowRun): io.syspulse.skel.wf.temporal.workflow.activity.ActivityResult = {
    import spray.json._

    val configId = run.cursor
    val config = configStore.???(configId).get

    log.info(s"DemoActivitiesImpl.executeActivity: configId=$configId, name=${config.name}")

    // Route to appropriate demo activity based on name
    val result = config.name.toLowerCase match {
      case "stepauto" => DemoActivities.executeStepAuto(config)
      case "stephuman" => DemoActivities.executeStepHuman(config)
      case _ =>
        log.warn(s"Unknown demo step: ${config.name}, using generic execution")
        executeGenericActivity(config)
    }

    // Store updated config
    import scala.concurrent.Await
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global
    val updated = Await.result(configStore.+(result), 10.seconds)

    // Extract output JsObject if present
    val output = updated.config
      .flatMap(_.asJsObject.fields.get("output"))
      .collect { case obj: JsObject => obj }
      .getOrElse(JsObject())

    // Build ActivityResult
    io.syspulse.skel.wf.temporal.workflow.activity.ActivityResult.success(updated.id, updated.name, output)
  }
}
