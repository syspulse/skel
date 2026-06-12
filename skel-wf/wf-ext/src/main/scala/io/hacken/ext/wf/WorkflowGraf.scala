package io.hacken.ext.wf

import spray.json._
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.service.JsonMap

// ============================================================================
// WorkflowGraf
//
// Visual representation of a Workflow as a DAG (Direct Acyclic Graph).
//
// A WorkflowGraf is BOTH a `template` and a `runtime instance`:
//   - WorkflowGraf.cid == None    -> `template`        (belongs to a WorkflowSchema)
//   - WorkflowGraf.cid == Some(id) -> `runtime instance` (belongs to a WorkflowConfig)
//
// The graph supports many-to-many connections to the same Node, therefore both
// WorkflowGraf and WorkflowNode keep Maps of WorkflowLink-s to navigate quickly
// back and forth (the `cursor` concept). The framework MUST keep WorkflowNode.links
// in sync with WorkflowGraf.links (see `sync`, `withNode`, `withLink`).
// ============================================================================

object WorkflowNode {
  type ID = Int

  // default visual `meta` for rendering (react-flow). Any visual attribute defined
  // in `meta` overrides the corresponding `style` attribute.
  val defaultMeta: Map[String, Any] = Map(
    "pos_x" -> 0,
    "pos_y" -> 0,
    "size_width" -> 100,
    "size_height" -> 100,
    "color" -> "white",
    "border" -> "1px solid black",
    "style" -> "node.style1",
  )
}

case class WorkflowNode(
  id: WorkflowNode.ID,         // internal uniq id of the node within the graph
  name: String,                // name of the node (by default DetectorSchema.title, user editable)

  sid: Int,                    // DetectorSchema ID reference (maps detector schema for this node)
  cid: Option[Int] = None,     // DetectorConfig ID reference.
                               //   set when WorkflowConfig is created and DetectorConfigs are linked.
                               //   allows re-using existing detectors to build a workflow.
                               //   Detector under `cid` must match `sid` schema.

  typ: Option[String] = Some("detector"), // node type (`detector` -> DetectorSchema), reserved for future use
  icon: Option[String] = None,            // optional icon, derived from DetectorSchema.icon
  tags: Seq[String] = Seq(),              // custom tags (NOT derived from DetectorSchema.tags)
  desc: Option[String] = None,            // optional custom description, derived from DetectorSchema.description

  // `meta` holds visual information for rendering (position, size, colors, style).
  // Visual attributes defined here override `style` attributes if present.
  meta: Option[Map[String, Any]] = Some(WorkflowNode.defaultMeta),

  data: Option[JsObject] = None,          // optional data for this node

  // multiple `to` and `from` links can be attached to the same node.
  // `links` allow to navigate quickly directly from this node back and forth.
  // ATTENTION: must be kept in sync with WorkflowGraf.links.
  links: Map[WorkflowLink.ID, WorkflowLink] = Map(),
)

object WorkflowLink {
  type ID = Int

  val defaultMeta: Map[String, Any] = Map(
    "color" -> "black",
    "border" -> "1px solid black",
    "textAlign" -> "center",
  )
}

case class WorkflowLink(
  id: WorkflowLink.ID,         // internal uniq id of the link
  from: WorkflowNode.ID,       // from WorkflowNode.id (-1 means no source node)
  to: WorkflowNode.ID,         // to WorkflowNode.id   (-1 means no target node)
  typ: Option[String] = None,  // connection type (reserved, e.g. -> or <->)

  meta: Option[Map[String, Any]] = Some(WorkflowLink.defaultMeta), // visual metadata
  data: Option[JsObject] = None,                                   // optional data
)

object WorkflowGraf {
  type ID = Int

  val NO_NODE: WorkflowNode.ID = -1

  /** Rebuild every WorkflowNode.links from the authoritative WorkflowGraf.links,
    * attaching each link to BOTH its `from` and `to` nodes. */
  def sync(graf: WorkflowGraf): WorkflowGraf = {
    val nodes1 = graf.nodes.map { case (nid, node) =>
      val nodeLinks = graf.links.filter { case (_, l) => l.from == nid || l.to == nid }
      nid -> node.copy(links = nodeLinks)
    }
    graf.copy(nodes = nodes1)
  }
}

case class WorkflowGraf(
  id: WorkflowGraf.ID,         // internal unique id of the graph

  sid: Option[Int] = None,     // WorkflowSchema id this graf represents (template source)
  cid: Option[Int] = None,     // WorkflowConfig id. None -> `template`, Some -> `runtime instance`

  // The graph supports multiple connections to/from the same Node.
  // `nodes` and `links` allow quick access by IDs (avoids high-complexity traversal).
  // ATTENTION: nodes.links must always be kept in sync with `links` (use `sync`).
  nodes: Map[WorkflowNode.ID, WorkflowNode] = Map(),
  links: Map[WorkflowLink.ID, WorkflowLink] = Map(),

  meta: Option[Map[String, Any]] = None, // optional metadata for the graph
  data: Option[JsObject] = None,         // optional data for the graph
) {

  def isTemplate: Boolean = cid.isEmpty
  def isInstance: Boolean = cid.isDefined

  /** Add (or replace) a node and keep links in sync. */
  def withNode(node: WorkflowNode): WorkflowGraf =
    WorkflowGraf.sync(copy(nodes = nodes + (node.id -> node)))

  /** Add (or replace) a link and keep node.links in sync. */
  def withLink(link: WorkflowLink): WorkflowGraf =
    WorkflowGraf.sync(copy(links = links + (link.id -> link)))

  /** Resolve the next nodes reachable from `nodeId` following outgoing links. */
  def next(nodeId: WorkflowNode.ID): Seq[WorkflowNode] =
    links.values.filter(_.from == nodeId).flatMap(l => nodes.get(l.to)).toSeq

  /** Resolve the previous nodes pointing to `nodeId` following incoming links. */
  def prev(nodeId: WorkflowNode.ID): Seq[WorkflowNode] =
    links.values.filter(_.to == nodeId).flatMap(l => nodes.get(l.from)).toSeq
}

object WorkflowGrafJson extends JsonCommon {
  import DefaultJsonProtocol._

  // arbitrary visual metadata: Map[String,Any] (Option[Map] relies on optionFormat -> None when absent)
  implicit val jf_metaMap: JsonFormat[Map[String, Any]] = JsonMap.mapFormat

  // spray-json's default mapFormat only supports String keys; WorkflowGraf uses Int keys.
  def intKeyMapFormat[V: JsonFormat]: RootJsonFormat[Map[Int, V]] = new RootJsonFormat[Map[Int, V]] {
    def write(m: Map[Int, V]): JsValue = JsObject(m.map { case (k, v) => k.toString -> v.toJson })
    def read(json: JsValue): Map[Int, V] = json match {
      case JsObject(fields) => fields.map { case (k, v) => k.toInt -> v.convertTo[V] }
      case JsNull           => Map.empty
      case x                => deserializationError(s"Expected Int-keyed object, got $x")
    }
  }

  implicit val jf_wf_link: RootJsonFormat[WorkflowLink] = jsonFormat6(WorkflowLink)
  implicit val jf_linkMap: RootJsonFormat[Map[Int, WorkflowLink]] = intKeyMapFormat[WorkflowLink]
  implicit val jf_wf_node: RootJsonFormat[WorkflowNode] = jsonFormat11(WorkflowNode)
  implicit val jf_nodeMap: RootJsonFormat[Map[Int, WorkflowNode]] = intKeyMapFormat[WorkflowNode]
  implicit val jf_wf_graf: RootJsonFormat[WorkflowGraf] = jsonFormat7(WorkflowGraf)
}
