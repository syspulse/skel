package io.hacken.ext.wf

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.Logger

import spray.json._
import io.syspulse.skel.service.JsonCommon
import java.util.concurrent.TimeUnit

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

import io.hacken.ext.detector.DetectorSchema
import io.hacken.ext.detector.DetectorConfig

case class WorkflowSchemaFaq(
  name: String,
  value: String
)

case class WorkflowSchemaNode(
  id: Int,      // internal uniq id
  name: String, // name of the node (by default it is detector title, but can be changed by user)  
  
  sid: Int,     // DetectorSchema ID reference to map detector schema for this node

  typ: Option[String] = None, // type of the node (`detector` -> DetectorSchema, reserved for future use, default is `detector`)
  icon: Option[String] = None, // optional icon for this node 

  schema: DetectorSchema, // Schema of the node
)

case class WorkflowSchemaConnection(
  id: Int,      // internal uniq id
  from: Int,    // from node id
  to: Int,      // to node id
  typ: Option[String] = None  // connection type (reserved for future use,e.g. -> or <->)
)

case class WorkflowSchema(
  id: Int,      // internal unique id
  createdAt: Long, // timestamp of creation
  updatedAt: Long, // timestamp of last update
  status: String, //"ACTIVE, DISABLED, DELETED",
  name: String, // corresponds to workflowType (e.g. `WorkflowAudit`, `WorkflowPoR`) 
  version: String, //"0.2.7",
  title: String,  // UI title (user title)  
  description: String, // description of the workflow
  author: String, // author of the workflow 
  icon: Option[String], // icon of the workflow
  faq: Option[Seq[WorkflowSchemaFaq]], // FAQ of the workflow (list of FAQ items)
  tags: Seq[String], // tags of the workflow
  nodes: Seq[WorkflowSchemaNode], // list of nodes in the workflow
  connections: Seq[WorkflowSchemaConnection], // list of connections between nodes
)

object WorkflowSchemaJson extends JsonCommon {
  import io.hacken.ext.detector.DetectorSchemaJson._
  implicit val jf_wf_faq_item: RootJsonFormat[WorkflowSchemaFaq] = jsonFormat2(WorkflowSchemaFaq)
  implicit val jf_wf_ws_node: RootJsonFormat[WorkflowSchemaNode] = jsonFormat6(WorkflowSchemaNode)
  implicit val jf_wf_ws_conn: RootJsonFormat[WorkflowSchemaConnection] = jsonFormat4(WorkflowSchemaConnection)
  implicit val jf_wf_ws: RootJsonFormat[WorkflowSchema] = jsonFormat14(WorkflowSchema.apply _)
}

