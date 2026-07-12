package io.syspulse.skel.wf.ext.engine

import spray.json._
import io.syspulse.skel.service.JsonCommon

import io.hacken.ext.wf.{WorkflowConfig, WorkflowNode}
import io.hacken.ext.detector.DetectorConfig

// ============================================================================
// EngineMapper
//
// Maps an observed Engine runtime instance (EngineWorkflow) onto the wf-ext
// WorkflowConfig / DetectorConfig model.
//
//   - EngineWorkflow.status  -> WorkflowConfig runtime status (COMPLETED / RUNNING / ...)
//   - EngineActivity / Child  -> DetectorConfig state, correlated by name:
//        rule 1: Activity Type name       == DetectorConfig.name
//        rule 2: Child Workflow Type name == DetectorConfig.name
//     (rules 3 & 4 - DetectorConfig.meta["wid"]/["cid"] - are not applicable to the
//      current DetectorConfig model, which has no `meta` field; name matching fully
//      covers the live PoR-Flow activity types e.g. ProofOfOwnership/ProofOfReserve.)
//
// The correlation of a WorkflowConfig to a runtime instance is by `xid` == runtimeId
// (Temporal RunId) - a STRICT requirement.
// ============================================================================

/** Runtime state of a single workflow step (DetectorConfig / WorkflowNode). */
case class DetectorRuntimeState(
  nodeId: Int,                 // WorkflowNode.id (or synthetic index when no config linked)
  cid: Option[Int],            // DetectorConfig id (WorkflowNode.cid)
  name: String,                // DetectorConfig.name (== Activity/Child type when matched)
  status: String,              // EngineStatus (UNKNOWN when not observed on the engine)
  kind: Option[String] = None, // ACTIVITY | CHILD_WORKFLOW
  runtimeId: Option[String] = None, // child-workflow RunId, when the step is a child workflow
  matched: Boolean = false,    // whether a runtime activity/child was found for this node
)

/** Combined view: a runtime instance mapped onto the (optional) linked WorkflowConfig. */
case class WorkflowRuntimeView(
  runtimeId: String,            // Temporal RunId == WorkflowConfig.xid
  name: String,                 // Temporal Workflow Type
  status: String,               // mapped WorkflowConfig runtime status
  namespace: String,
  cid: Option[Int],             // linked WorkflowConfig.id (if any)
  sid: Option[Int],             // linked WorkflowSchema.id (if any)
  steps: Seq[DetectorRuntimeState],
  runtime: EngineWorkflow,      // full engine tree (activities + child workflows)
)

object EngineMapper {

  /** Map workflow-level status (engine status vocabulary is already normalized). */
  def workflowStatus(w: EngineWorkflow): String = w.status

  /**
   * Find, among a workflow's activities and (recursive) child workflows, the runtime element
   * whose Type name matches `name` (resolution rules 1 & 2). Activities take precedence.
   */
  def matchByName(w: EngineWorkflow, name: String): Option[(String, String, Option[String])] = {
    // returns (status, kind, runtimeId)
    w.allActivities.find(_.name == name).map(a => (a.status, a.kind, None: Option[String]))
      .orElse(
        w.flatten.drop(1).find(_.name == name) // drop(1): skip the root workflow itself
          .map(c => (c.status, EngineActivity.KIND_CHILD, Some(c.runtimeId)))
      )
  }

  /**
   * Map a runtime EngineWorkflow onto a WorkflowConfig (if provided) plus its DetectorConfigs.
   *
   * @param w          observed runtime instance
   * @param config     the WorkflowConfig linked by xid (None -> steps derived directly from engine)
   * @param detectors  cid -> DetectorConfig for the config's nodes
   */
  def map(w: EngineWorkflow,
          config: Option[WorkflowConfig] = None,
          detectors: Map[Int, DetectorConfig] = Map()): WorkflowRuntimeView = {

    val steps: Seq[DetectorRuntimeState] = config match {
      case Some(c) =>
        c.graph.nodes.values.toSeq.sortBy(_.id).map { node =>
          val dc   = node.cid.flatMap(detectors.get)
          val name = dc.map(_.name).getOrElse(node.title)
          matchByName(w, name) match {
            case Some((status, kind, rid)) =>
              DetectorRuntimeState(node.id, node.cid, name, status, Some(kind), rid, matched = true)
            case None =>
              DetectorRuntimeState(node.id, node.cid, name, EngineStatus.UNKNOWN, None, None, matched = false)
          }
        }

      case None =>
        // no linked config: expose observed activities/children as steps directly
        val acts = w.allActivities.map { a =>
          DetectorRuntimeState(-1, None, a.name, a.status, Some(a.kind), None, matched = true)
        }
        val kids = w.flatten.drop(1).map { c =>
          DetectorRuntimeState(-1, None, c.name, c.status, Some(EngineActivity.KIND_CHILD), Some(c.runtimeId), matched = true)
        }
        acts ++ kids
    }

    WorkflowRuntimeView(
      runtimeId = w.runtimeId,
      name = w.name,
      status = workflowStatus(w),
      namespace = w.namespace,
      cid = config.map(_.id),
      sid = config.map(_.sid),
      steps = steps,
      runtime = w,
    )
  }
}

object EngineMapperJson extends JsonCommon {
  import EngineJson._
  implicit val jf_det_runtime_state: RootJsonFormat[DetectorRuntimeState] = jsonFormat7(DetectorRuntimeState.apply)
  implicit val jf_wf_runtime_view: RootJsonFormat[WorkflowRuntimeView]     = jsonFormat8(WorkflowRuntimeView.apply)
}
