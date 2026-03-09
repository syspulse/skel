package io.syspulse.skel.wf.temporal.workflow.server

import io.hacken.ext.wf._

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
  conns: Option[Seq[WorkflowSchemaConnection]] = None
)

case class WorkflowUpdateReq(
  name: Option[String] = None,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  tags: Option[Seq[String]] = None,
  nodes: Option[Seq[WorkflowSchemaNode]] = None,
  conns: Option[Seq[WorkflowSchemaConnection]] = None
)
