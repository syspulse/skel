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
   * @param args Encoded activity arguments (configId: Int)
   * @return Encoded result (configId: Int)
   */
  override def execute(args: EncodedValues): Object = {
    // Get activity name from execution context
    val activityName = Activity.getExecutionContext.getInfo.getActivityType
    log.info(s"Executing dynamic activity: ${activityName}")

    // Decode arguments (expecting single Int parameter: configId)
    val configId = args.get(0, classOf[Int])
    log.info(s"Activity ${activityName} called with configId: ${configId}")

    // Execute via GenericActivities implementation (GenericActivitiesImpl or Por2ActivitiesImpl)
    val result = activities.executeActivity(configId)
    log.info(s"Activity ${activityName} completed: ${result}")

    // Return result as Integer (boxed for Java compatibility)
    Int.box(result)
  }
}
