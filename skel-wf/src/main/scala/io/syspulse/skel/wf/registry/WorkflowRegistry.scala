package io.syspulse.skel.wf.registy

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime._

class WorkflowRegistry(default:Seq[Exec]) {
  val log = Logger(s"${this}")

  val flowlets:Map[Exec.ID,Exec] = default.map(f => f.typ -> f).toMap  
  
  log.info(s"flowlets: ${flowlets}")

  def resolve(name:String): Option[Exec] = {
    flowlets.get(name)
  }

}
