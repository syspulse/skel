package io.syspulse.skel.wf.registy

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime._

class WorkflowRegistry(default:Seq[Flowlet]) {
  val log = Logger(s"${this}")

  val flowlets:Map[Flowlet.ID,Flowlet] = default.map(f => f.typ -> f).toMap  
  
  log.info(s"flowlets: ${flowlets}")

  def resolve(name:String): Option[Flowlet] = {
    flowlets.get(name)
  }

}
