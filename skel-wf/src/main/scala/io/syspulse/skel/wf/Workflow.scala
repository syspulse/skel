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

  // F1(LogExec(sys=1,log.level=WARN)) -> F2(LogExec(sys=2)) -> F3(TerminateExec())
  def assemble(dsl:String,data:Map[String,Any] = Map()):Try[Workflow] = {
    
    def assembleExec(name:String,typ:String,data:Option[String]) = {
      val typWithClass = if(typ.contains(".")) typ else s"io.syspulse.skel.wf.exec.${typ}"
      val dataMap = if(data.isDefined) Some(data.get.split("[,=]").grouped(2).map(a => a(0) -> a(1)).toMap) else None
      Exec(name,typWithClass,in = Seq(In("in-0")), out = Seq(Out("out-0")),data = dataMap)
    }

    val ee = dsl.split("->").map(_.trim).filter(!_.isEmpty()).map( e => {
      e.split("[()]").toList match {
        case name :: typ :: data :: Nil =>           
          Success(assembleExec(name,typ,Some(data)))
        case name :: typ :: Nil => 
          Success(assembleExec(name,typ,None))
        case _ => 
          Failure(new Exception(s"failed to parse: ${e}"))
      }
    }).toList

    val eeFailed = ee.filter(_.isFailure)
    if(eeFailed.size > 0) 
      return eeFailed.head.map(_ => this)

    def assembleLinks(ee:List[Exec]):Seq[Link] = {
      ee match {
        case exec1 :: exec2 :: Nil  => 
          Seq(linkExecs(exec1,exec2,0,0).get)
        case exec1 :: execs =>
          Seq(linkExecs(exec1,execs.head,0,0).get) ++ assembleLinks(execs)
        case exec1 :: Nil =>
          // one exec, not links
          Seq()
        case Nil => Seq()
      }
    }

    val flow = ee.map(_.toOption.get)
    // create links
    val links = assembleLinks(flow)

    Success(Workflow(id,name,Map(),flow,links))
  }
}

object Workflow {
  type ID = String

  def assemble(id:Workflow.ID,name:String,dsl:String,data:Map[String,Any] = Map()):Try[Workflow] = {
    for {
      w1 <- Success(Workflow(id,name))
      w2 <- w1.assemble(dsl,data)
    } yield w2
  }
}
