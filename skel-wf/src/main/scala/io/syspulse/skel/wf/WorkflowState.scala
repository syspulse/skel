package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.ExecData
import io.syspulse.skel.wf.runtime.Executing

case class State(ts:Long,eid:Executing.ID,data:ExecData,status:Option[String] = None)

case class WorkflowState(wid: Workflowing.ID, states:Seq[State], var count:Long = 0, ts0:Long = System.currentTimeMillis)

// object WorkflowState {
//   type ID = String
// }
