package io.syspulse.skel.wf.temporal.por

import io.temporal.activity.Activity

/**
 * Common helpers for Temporal Activities logging.
 *
 * Provides a consistent "[wid / rid]" tag for logs, where:
 * - wid = log tag "[workflowId / runId]"
 * - workflowId = workflow ID from activity context
 */
trait ActivityLogging {

  protected def activityInfo = Activity.getExecutionContext.getInfo
  protected def workflowId: String = activityInfo.getWorkflowId
  protected def runId: String = activityInfo.getRunId
  /** Log tag: "[workflowId / runId]" */
  protected def wid: String = s"[$workflowId / ${runId}]"
}

