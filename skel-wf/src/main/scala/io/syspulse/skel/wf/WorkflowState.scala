package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.ExecData
import io.syspulse.skel.wf.runtime.Executing

case class State(ts:Long,eid:Executing.ID,data:ExecData,status:Option[String] = None)

case class WorkflowState(wid: Workflowing.ID, status:WorkflowState.Status, states:Seq[State]=Seq(), var count:Long = 0, ts0:Long = System.currentTimeMillis)

object WorkflowState {
  type Status = String

  val STATUS_CREATED = "CREATED"
  val STATUS_INITIALIZED = "INIT"
  val STATUS_STOPPED = "STOPPED"
  val STATUS_RUNNING = "RUNNING"
}
