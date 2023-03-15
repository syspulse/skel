package io.syspulse.skel.wf.registry

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime._

class WorkflowRegistry(default:Seq[Exec] = Seq()) {
  val log = Logger(s"${this}")

  val execs:Map[Exec.ID,Exec] = default.map(f => f.typ -> f).toMap  
  
  log.info(s"execs: ${execs}")

  def resolve(name:String): Option[Exec] = {
    execs.get(name)
  }

}
