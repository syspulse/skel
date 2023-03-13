package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.store.WorkflowStateStore

case class Workflow(
  id:Workflow.ID,
  data:ExecData,
  flow: Seq[Exec],
  links: Seq[Link]) {
  
  def getData = data
}

object Workflow {
  type ID = String
}
