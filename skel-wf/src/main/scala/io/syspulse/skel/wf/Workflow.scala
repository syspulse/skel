package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.store.WorkflowStateStore

case class Workflow(  
  id:Workflow.ID,
  var name:String,
  var data:Map[String,Any] = Map(),  // global workflow data
  var flow: Seq[Exec] = Seq(),
  var links: Seq[Link] = Seq()
  ) {
  
  def getData = data  

  def addData(d:Map[String,Any]):Try[Workflow] = {
    data = data ++ d
    Success(this)
  }

  def addExec(e:Exec):Try[Workflow] = {
    flow = flow :+ e
    Success(this)
  }

  def delExec(eid:Exec.ID):Try[Workflow] = {
    flow = flow.filter( e => {
      if(e.getId == eid) {
        // remove all links to this Exec
        links = links.filter( l => l.from != eid && l.to != eid )
        false
      } else
        true
    })
    Success(this)
  }

  def addLink(l:Link):Try[Workflow] = {
    links = links :+ l
    Success(this)
  }

  // simple linking
  // E1(out-1) -> E(in-1)
  def linkExecs(e1:Exec,e2:Exec,i1:Int = 0,i2:Int = 0):Try[Link] = {
    val name = s"${e1.name}:${e1.out(i1).id}---${e2.name}:${e2.in(i2).id}"
    val l = Link(name,e1.name,e1.out(i1).id,e2.name,e2.in(i2).id)
    Success(l)
  }

  def update(name:Option[String]=None,data:Option[Map[String,Any]]=None):Try[Workflow] = {
    for {
      w0 <- Success(this)
      w1 <- Success(if(name.isDefined) w0.copy(name = name.get) else w0)
      w2 <- Success(if(data.isDefined) w1.copy(data = data.get) else w1)      
    } yield w2
  }
}

object Workflow {
  type ID = String
}
