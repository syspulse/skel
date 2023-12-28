package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.store.WorkflowStateStore
import io.syspulse.skel.wf.registry.WorkflowRegistry

case class Workflow(  
  id:Workflow.ID,
  var name:String,
  var data:Map[String,Any] = Map(),  // global workflow data
  var execs: Seq[Exec] = Seq(),
  var links: Seq[Link] = Seq()
  ) {
  
  def getData = data  

  def addData(d:Map[String,Any]):Try[Workflow] = {
    data = data ++ d
    Success(this)
  }

  def addExec(e:Exec):Try[Workflow] = {
    execs = execs :+ e
    Success(this)
  }

  def delExec(eid:Exec.ID):Try[Workflow] = {
    execs = execs.filter( e => {
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
  // F1(LogExcec())[out-0] -> F1[in-0]
  // F1([in-0]LogExcec()[out-0]) -> F1([in-0]())
  def assemble(script:String,data:Map[String,Any] = Map())(implicit registry:WorkflowRegistry):Try[Workflow] = {
    
    var execsExecs:Map[String,Exec] = Map()

    def assembleExec(execs:Map[String,Exec],name0:String,typ:String,data:Option[String],outlet:String = "out-0",update:Boolean=true) = {
      val (name,inlet) = name0.split("[\\[\\]]").toList match {
        case name :: inlet :: Nil => (name,inlet)
        case name :: Nil => (name,"in-0")
      }
      
      if(typ == "") { 
        // reference to existing Exec, update lets
        execs.get(name) match {
          case Some(exec) =>             
            if(update) {
              val exec1 = exec.in.find(_.id == inlet) match {
                case Some(_) => exec
                case None => exec.copy(in = exec.in :+ In(inlet))
              }
              val exec2 = exec1.out.find(_.id == outlet) match {
                case Some(_) => exec1
                case None => exec1.copy(out = exec1.out :+ Out(outlet))
              }
              // update map
              // execs = execs + (exec2.getId -> exec2)    
              Success(exec2)
            } else
              Success(exec.copy(in = Seq(In(inlet)),out=Seq(Out(outlet))))
          case None => 
            Failure(new Exception(s"Exec not found: ${name}"))
        }
      } else {
        val typWithClass = {
          // resolve           
          if(typ.contains(".")) 
            typ 
          else 
            //s"io.syspulse.skel.wf.exec.${typ}"
            registry.resolve(typ).map(e => e.typ).getOrElse(typ)            
        }
        val dataMap = if(data.isDefined) Some(data.get.split("[,=]").grouped(2).map(a => a(0) -> {if(a.size>1) a(1) else ""}).toMap) else None
        val ex = Exec(name,typWithClass,in = Seq(In(inlet)), out = Seq(Out(outlet)),data = dataMap)
        // execs = execs + (ex.getId -> ex)
        Success(ex)
      }
    }

    def parseExec(e:String,execs:Map[String,Exec],update:Boolean=true) = {
      e.split("[()]").toList match {
        case name :: typ :: data :: outlet :: Nil if(outlet.startsWith("[")) =>
          assembleExec(execs,name,typ,if(data.isEmpty()) None else Some(data),outlet.stripPrefix("[").stripSuffix("]"),update=update)
        case name :: typ :: data :: "" :: outlet :: Nil if(outlet.startsWith("[")) =>
          assembleExec(execs,name,typ,if(data.isEmpty()) None else Some(data),outlet.stripPrefix("[").stripSuffix("]"),update=update)
        case name :: typ :: outlet :: Nil if(outlet.startsWith("[")) =>
          assembleExec(execs,name,typ,None,outlet.stripPrefix("[").stripSuffix("]"),update=update)
        case name :: "" :: outlet :: Nil if(outlet.startsWith("[")) =>
          assembleExec(execs,name,"",None,outlet.stripPrefix("[").stripSuffix("]"),update=update)
        case name :: typ :: data :: Nil =>
          assembleExec(execs,name,typ,if(data.isEmpty()) None else Some(data),update=update)
        case name :: typ :: Nil => 
          assembleExec(execs,name,typ,None,update=update)
        case name :: Nil => 
          assembleExec(execs,name,"",None,update=update)
        case _ => 
          Failure(new Exception(s"failed to parse: ${e}"))
      }
    }

    val ee = script.split("\n").filter(!_.trim.isEmpty).map(dsl => 
      dsl.split("->").map(_.trim).filter(!_.isEmpty()).map( e => {
        parseExec(e,execsExecs,true).map(ex => {
          execsExecs = execsExecs + (ex.getId -> ex)
          ex
        })
      })
    ).flatten
    
    val eeFailed = ee.filter(_.isFailure)
    if(eeFailed.size > 0) {
      return eeFailed.head.map(_ => this)
    }

    def assembleLinks(ee:Seq[Exec]):Seq[Link] = {
      //println(s"${ee}")
      ee.toList match {
        case exec1 :: exec2 :: Nil  => 
          Seq(linkExecs(exec1,exec2,0,0).get)
        case exec1 :: Nil =>
          // one exec, not links
          Seq()
        case exec1 :: execs =>
          Seq(linkExecs(exec1,execs.head,0,0).get) ++ assembleLinks(execs)        
        case Nil => 
          Seq()
      }
    }    

    val flow = execsExecs.values.toList

    var execsLinks:Map[String,Exec] = Map()
    // create links
    //val links = assembleLinks(ee.map(_.toOption.get))

    // treat each line as possible pipeline
    val links = script.split("\n").filter(!_.trim.isEmpty).map(dsl => {
      val ee = dsl.split("->").map(_.trim).filter(!_.isEmpty()).map(line => {
        val ex = parseExec(line,execsLinks,false).get
        execsLinks = execsLinks + (ex.getId -> ex)
        ex     
      })

      assembleLinks(ee.toIndexedSeq)

    }).flatten.toSeq
        

    Success(Workflow(id,name,Map(),flow,links))
  }
}

object Workflow {
  type ID = String

  def assemble(id:Workflow.ID,name:String,dsl:String,data:Map[String,Any] = Map())(implicit registry:WorkflowRegistry):Try[Workflow] = {
    for {
      w1 <- Success(Workflow(id,name))
      w2 <- w1.assemble(dsl,data)
    } yield w2
  }
}
