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

  override def executeActivity(configId: Int): Int = {
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
    configStore.+(result).get.id
  }
}
