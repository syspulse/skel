package io.syspulse.skel.wf.ext.engine

import spray.json._
import io.syspulse.skel.service.JsonCommon
import io.hacken.ext.wf.WorkflowStatus

// ============================================================================
// Engine runtime models (engine-agnostic).
//
// These describe the state of a running Workflow instance and its Activities /
// Child-Workflows as observed on the orchestration Engine (Temporal being the
// primary implementation). They are the intermediate representation that the
// EngineMapper maps onto WorkflowConfig / DetectorConfig state.
//
//   EngineWorkflow  <->  WorkflowConfig   (runtimeId == WorkflowConfig.xid)
//   EngineActivity  <->  DetectorConfig   (matched by name / type)
// ============================================================================

/**
 * Temporal -> WorkflowStatus mapping (the Temporal Engine's status mapper).
 *
 * The canonical status vocabulary now lives in `io.hacken.ext.wf.WorkflowStatus` (shared by
 * WorkflowConfig / DetectorConfig). This object only converts Temporal-specific engine states into
 * WorkflowStatus values, and re-exposes those values under short names for engine-internal use.
 */
object EngineStatus {
  // re-exposed WorkflowStatus constants (all engine states ARE WorkflowStatus values)
  val NEW               = WorkflowStatus.NEW
  val SCHEDULED         = WorkflowStatus.SCHEDULED
  val RUNNING           = WorkflowStatus.RUNNING
  val RUNNING_FAILED   = WorkflowStatus.RUNNING_FAILED
  val WAITING           = WorkflowStatus.WAITING
  val PAUSED            = WorkflowStatus.PAUSED
  val COMPLETED         = WorkflowStatus.COMPLETED
  val FAILED            = WorkflowStatus.FAILED
  val TERMINATED        = WorkflowStatus.TERMINATED
  val CANCELED          = WorkflowStatus.CANCELED
  val TIMED_OUT         = WorkflowStatus.TIMED_OUT
  val CONTINUED_AS_NEW  = WorkflowStatus.CONTINUED_AS_NEW
  val UNKNOWN           = WorkflowStatus.UNKNOWN
  val UNRESOLVED        = WorkflowStatus.UNRESOLVED

  val all: Set[String] = WorkflowStatus.all

  /**
   * Map a Temporal WorkflowExecutionStatus (enum name or short name) to a WorkflowStatus value.
   * Accepts both the proto enum name (WORKFLOW_EXECUTION_STATUS_RUNNING) and the
   * CLI/short name (Running).
   */
  def fromTemporalWorkflow(s: String): String = {
    val n = normalize(s)
    n match {
      case "RUNNING"          => RUNNING
      case "COMPLETED"        => COMPLETED
      case "FAILED"           => FAILED
      case "CANCELED" | "CANCELLED" => CANCELED
      case "TERMINATED"       => TERMINATED
      case "CONTINUEDASNEW" | "CONTINUED_AS_NEW" => CONTINUED_AS_NEW
      case "TIMEDOUT" | "TIMED_OUT" => TIMED_OUT
      case "UNSPECIFIED" | "" => UNKNOWN
      case _                  => UNKNOWN
    }
  }

  /**
   * Map a Temporal activity lifecycle (derived from event history) to a WorkflowStatus value.
   */
  def fromTemporalActivity(s: String): String = {
    val n = normalize(s)
    n match {
      case "SCHEDULED"        => SCHEDULED
      case "STARTED" | "RUNNING" => RUNNING
      case "COMPLETED"        => COMPLETED
      case "FAILED"           => FAILED
      case "CANCELED" | "CANCELLED" => CANCELED
      case "TIMEDOUT" | "TIMED_OUT" => TIMED_OUT
      case _                  => UNKNOWN
    }
  }

  /** Strip proto prefixes/underscores and uppercase for tolerant matching. */
  private def normalize(s: String): String = {
    val u = Option(s).getOrElse("").trim.toUpperCase
    val stripped = List(
      "WORKFLOW_EXECUTION_STATUS_",
      "EVENT_TYPE_ACTIVITY_TASK_",
      "ACTIVITY_TASK_",
      "CHILD_WORKFLOW_EXECUTION_"
    ).foldLeft(u)((acc, p) => acc.stripPrefix(p))
    stripped.replace("_", "")
  }

  /** Terminal (closed) statuses. */
  def isTerminal(status: String): Boolean = WorkflowStatus.isTerminal(status)
}

/**
 * A single Activity or Child-Workflow observed inside a running/closed Workflow.
 *
 * `name` is the Temporal Activity Type or Child-Workflow Type name - the value used
 * to correlate with a DetectorConfig (resolution rules 1 & 2 in the requirements).
 */
case class EngineActivity(
  id: String,                      // Temporal activityId, or child WorkflowId for child workflows
  name: String,                    // Activity Type name / Child-Workflow Type name
  kind: String,                    // "ACTIVITY" | "CHILD_WORKFLOW"
  status: String,                  // normalized EngineStatus
  startedAt: Option[Long] = None,  // epoch millis
  closedAt: Option[Long] = None,   // epoch millis
  runtimeId: Option[String] = None,// child workflow RunId (for CHILD_WORKFLOW)
  detail: Option[String] = None,   // optional failure/cancel reason
  meta: Map[String, String] = Map(), // engine-specific extras (e.g. "cid" published by the activity)
)

object EngineActivity {
  val KIND_ACTIVITY = "ACTIVITY"
  val KIND_CHILD    = "CHILD_WORKFLOW"
}

/**
 * A runtime Workflow instance on the Engine.
 *
 *   id        -> Temporal WorkflowId
 *   runtimeId -> Temporal RunId  (correlates to WorkflowConfig.xid - STRICT requirement)
 *   name      -> Temporal Workflow Type name  (correlates to WorkflowConfig.name)
 *
 * Child workflows share the parent WorkflowId prefix but have their own RunId; they are
 * carried in `children`.
 */
case class EngineWorkflow(
  id: String,                       // Temporal WorkflowId
  runtimeId: String,                // Temporal RunId
  name: String,                     // Temporal Workflow Type name
  status: String,                   // normalized EngineStatus
  namespace: String,
  startedAt: Option[Long] = None,
  closedAt: Option[Long] = None,
  taskQueue: Option[String] = None,
  parentId: Option[String] = None,  // parent WorkflowId (for child workflows)
  activities: Seq[EngineActivity] = Seq(),
  children: Seq[EngineWorkflow] = Seq(),
  meta: Map[String, String] = Map(), // engine-derived extras, e.g. "err" (a failing/retrying task's message)
) {
  /** All activities of this workflow AND (recursively) all child workflows, flattened. */
  def allActivities: Seq[EngineActivity] =
    activities ++ children.flatMap(_.allActivities)

  /** This workflow plus all descendants, flattened. */
  def flatten: Seq[EngineWorkflow] =
    this +: children.flatMap(_.flatten)
}

/** List wrapper for the runtime workflows API (`/engine/{engine}[/{namespace}]`). */
case class EngineWorkflows(workflows: Seq[EngineWorkflow], total: Long)

/** Result of starting a NEW workflow execution on the Engine (Temporal StartWorkflowExecution). */
case class EngineStart(workflowId: String, runtimeId: String, namespace: String)

object EngineJson extends JsonCommon {
  implicit val jf_engine_activity: RootJsonFormat[EngineActivity] = jsonFormat9(EngineActivity.apply)
  implicit val jf_engine_workflow: RootJsonFormat[EngineWorkflow] = rootFormat(lazyFormat(jsonFormat12(EngineWorkflow.apply)))
  implicit val jf_engine_workflows: RootJsonFormat[EngineWorkflows] = jsonFormat2(EngineWorkflows.apply)
}
