package io.syspulse.skel.wf.temporal.workflow.activity

import com.typesafe.scalalogging.Logger
import io.temporal.activity.{DynamicActivity, Activity}
import io.temporal.common.converter.EncodedValues

import io.syspulse.skel.wf.temporal.workflow.store.WorkflowConfigStore

/**
 * Dynamic Activity Handler
 *
 * Handles activities with dynamic names (business names from DetectorConfig.name)
 * Routes all activity invocations to GenericActivities.executeActivity()
 */
class DynamicActivityHandler(
  activities: GenericActivities
) extends DynamicActivity {

  private val log = Logger(getClass)

  /**
   * Execute activity with dynamic name
   *
   * @param args Encoded activity arguments (run: WorkflowRun)
   * @return Encoded result (ActivityResult, serialized to JSON by Temporal)
   */
  override def execute(args: EncodedValues): Object = {
    // Get activity name from execution context
    val activityName = Activity.getExecutionContext.getInfo.getActivityType
    log.info(s"Executing dynamic activity: ${activityName}")

    // Decode arguments (expecting single WorkflowRun parameter)
    val run = args.get(0, classOf[io.hacken.ext.wf.WorkflowRun])
    log.info(s"Activity ${activityName} called with cursor: ${run.cursor}")

    // Execute via GenericActivities implementation (GenericActivitiesImpl or Por2ActivitiesImpl)
    val result = activities.executeActivity(run)
    log.info(s"Activity ${activityName} completed: configId=${result.configId}, status=${result.status}")

    // Return ActivityResult (Temporal will serialize it to JSON)
    result
  }
}
