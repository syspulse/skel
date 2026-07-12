package io.syspulse.skel.wf.ext.engine

import spray.json._
import io.syspulse.skel.service.JsonCommon

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
 * Normalized workflow / activity status. Engine-specific states are folded into
 * this closed vocabulary so callers never depend on Temporal enum names.
 */
object EngineStatus {
  val NEW               = "NEW"                // known but not yet started
  val SCHEDULED         = "SCHEDULED"          // activity scheduled, not yet started
  val RUNNING           = "RUNNING"
  val WAITING           = "WAITING"            // running but blocked on a signal / human step
  val PAUSED            = "PAUSED"
  val COMPLETED         = "COMPLETED"
  val FAILED            = "FAILED"
  val TERMINATED        = "TERMINATED"
  val CANCELED          = "CANCELED"
  val TIMED_OUT         = "TIMED_OUT"
  val CONTINUED_AS_NEW  = "CONTINUED_AS_NEW"
  val UNKNOWN           = "UNKNOWN"

  val all: Set[String] = Set(
    NEW, SCHEDULED, RUNNING, WAITING, PAUSED, COMPLETED, FAILED,
    TERMINATED, CANCELED, TIMED_OUT, CONTINUED_AS_NEW, UNKNOWN
  )

  /**
   * Map a Temporal WorkflowExecutionStatus (enum name or short name) to EngineStatus.
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
   * Map a Temporal activity lifecycle (derived from event history) to EngineStatus.
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
  def isTerminal(status: String): Boolean = status match {
    case COMPLETED | FAILED | TERMINATED | CANCELED | TIMED_OUT | CONTINUED_AS_NEW => true
    case _ => false
  }
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

object EngineJson extends JsonCommon {
  implicit val jf_engine_activity: RootJsonFormat[EngineActivity] = jsonFormat9(EngineActivity.apply)
  implicit val jf_engine_workflow: RootJsonFormat[EngineWorkflow] = rootFormat(lazyFormat(jsonFormat11(EngineWorkflow.apply)))
  implicit val jf_engine_workflows: RootJsonFormat[EngineWorkflows] = jsonFormat2(EngineWorkflows.apply)
}
