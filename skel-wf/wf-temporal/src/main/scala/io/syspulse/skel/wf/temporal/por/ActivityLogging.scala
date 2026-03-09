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

  private lazy val _activityInfo = Activity.getExecutionContext.getInfo
  protected lazy val workflowId: String = _activityInfo.getWorkflowId
  /** Log tag: "[workflowId / runId]" */
  protected lazy val wid: String = s"[$workflowId / ${_activityInfo.getRunId}]"
}

