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

case class WorkflowSchemaFaq(
  name: String,
  value: String
)

case class WorkflowSchemaNode(
  id: Int,      // internal uniqu id
  name: String, // name of the node (by default it is detector title, but can be changed by user)   
  nid: String,  // Unique Node (Action) id (used to map to action inside detector). one detector can implement mulitple nodes / Actions. 
  typ: Option[String], // type of the node (`detector`, reserved for future use, default is `detector`)
  detector: Option[DetectorSchema]  // detector schema for this node
)

case class WorkflowSchemaConnection(
  id: Int,
  from: Int,
  to: Int,
  typ: String
)

case class WorkflowSchema(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  status: String, //"ACTIVE, DISABLED, DELETED",
  name: String, // Uniqie Workflow Name Id (e.g. WorkflowAudit). Same as for DetectorSchema.name
  version: String, //"0.2.7",
  title: String,  // UI title
  description: String,
  author: String,
  icon: Option[String],
  faq: Option[Seq[WorkflowSchemaFaq]],
  tags: Seq[String],  
  
  nodes: Seq[WorkflowSchemaNode],
  conns: Seq[WorkflowSchemaConnection],
)

object WorkflowSchemaJson extends JsonCommon {
  import io.hacken.ext.detector.DetectorSchemaJson._
  implicit val jf_wf_faq_item: RootJsonFormat[WorkflowSchemaFaq] = jsonFormat2(WorkflowSchemaFaq)
  implicit val jf_wf_ws_node: RootJsonFormat[WorkflowSchemaNode] = jsonFormat5(WorkflowSchemaNode)
  implicit val jf_wf_ws_conn: RootJsonFormat[WorkflowSchemaConnection] = jsonFormat4(WorkflowSchemaConnection)
  implicit val jf_wf_ws: RootJsonFormat[WorkflowSchema] = jsonFormat14(WorkflowSchema.apply _)
}

