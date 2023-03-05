package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._

case class Workflow(
  id:Workflow.ID,
  attributes:ExecData,
  store:String,
  flow: Seq[Exec],
  links: Seq[Link])(implicit engine:WorkflowEngine) {
  
  def getAttributes = attributes
  def getStore = store
}

object Workflow {
  type ID = String
}
