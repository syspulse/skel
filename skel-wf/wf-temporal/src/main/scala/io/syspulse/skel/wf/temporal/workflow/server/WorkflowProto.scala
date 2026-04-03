package io.syspulse.skel.wf.temporal.workflow.server

import io.hacken.ext.wf._
import io.syspulse.skel.wf.temporal._

case class WorkflowRes(id: Int)

case class Workflows(workflows: Seq[WorkflowSchema], total: Option[Int] = None)

case class WorkflowCreateReq(
  name: String,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  author: Option[String] = None,
  icon: Option[String] = None,
  faq: Option[Seq[WorkflowSchemaFaq]] = None,
  tags: Option[Seq[String]] = None,
  nodes: Option[Seq[WorkflowSchemaNode]] = None,
  connections: Option[Seq[WorkflowSchemaConnection]] = None
)

case class WorkflowUpdateReq(
  name: Option[String] = None,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  tags: Option[Seq[String]] = None,
  nodes: Option[Seq[WorkflowSchemaNode]] = None,
  connections: Option[Seq[WorkflowSchemaConnection]] = None
)

// Temporal workflow management DTOs
case class TemporalQueryReq(
  query: String = "",
  pageSize: Int = 10
)

case class TemporalListReq(
  status: Option[String] = None,
  workflowType: Option[String] = None,
  pageSize: Int = 10
)

// Workflow start DTOs
case class WorkflowStartReq(
  id: Int,                    // WorkflowSchema ID to use
  tid: Int,                   // Tenant ID
  pid: Int,                   // Project ID
  title: String,              // Workflow title (may contain placeholders like {name}-{tid}-{pid}-{ts})
  workflow: Option[String] = None  // Optional: workflow definition (e.g., "auto->human" for Demo)
)

case class WorkflowStartRes(
  wid: String,
  rid: String
)

// Workflow signal DTOs
case class WorkflowSignalReq(
  aid: String,      // Activity ID/name (e.g., "pol")
  data: spray.json.JsObject  // Activity-specific data
)

case class WorkflowSignalRes(
  success: Boolean,
  message: String
)

// Workflow step input DTOs
case class StepInputReq(
  stepId: String,
  data: spray.json.JsValue  // Step-specific data as JSON
)

case class StepInputRes(
  success: Boolean,
  message: String
)

// Workflow Run DTOs
case class WorkflowRunCreateReq(
  schemaId: Int,            // WorkflowSchema ID
  steps: Seq[Int],          // DetectorConfig IDs for each step
  tid: Option[String] = None,      // Transaction/Task ID for {tid} placeholder
  pid: Option[String] = None,      // Process/Project ID for {pid} placeholder
  project: Option[String] = None   // Project name for {project} placeholder
)

case class WorkflowRunCreateRes(
  wid: String,              // Workflow ID
  rid: String               // Run ID
)

case class WorkflowRunContinueReq(
  configId: Int             // DetectorConfig ID to continue from
)

case class WorkflowRunContinueRes(
  success: Boolean,
  message: String
)

case class WorkflowRuns(
  runs: Seq[io.hacken.ext.wf.WorkflowRun],
  total: Option[Int] = None
)
