package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.actor.typed.SupervisorStrategy

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.registy.WorkflowRegistry


trait WorkflowCommand

object WorkflowEngine {

  val rootBehavior = { 
    Behaviors.supervise[WorkflowCommand] { 
      workflow()
  }}.onFailure[Exception](SupervisorStrategy.resume)

  def workflow(): Behavior[WorkflowCommand] = {
    Behaviors.receiveMessage {
      case _ => Behaviors.same
    }
  }

  val as = ActorSystem[WorkflowCommand](rootBehavior, "WorfklowEngine")
}

class WorkflowEngine(store:String = "dir:///tmp/skel-wf") {
  val log = Logger(s"${this}")

  val storeWorkflow = s"${store}/workflow"  
  val storeRuntime = s"${store}/runtime"

  val registry = new WorkflowRegistry(Seq(
    Exec("Log","io.syspulse.skel.wf.exec.LogExec"),
    Exec("Process","io.syspulse.skel.wf.exec.ProcessExec"),
    Exec("Terminate","io.syspulse.skel.wf.exec.TerminateExec")
  ))

  // create stores
  os.makeDir.all(os.Path(s"${storeWorkflow}",os.pwd))
  os.makeDir.all(os.Path(s"${storeRuntime}",os.pwd))

  def getStoreRuntime() = storeRuntime
  def getStoreWorkflow() = storeWorkflow

  def spawn(wf:Workflow):Try[Workflowing] = {
    val wid = Workflowing.id(wf)
    val w = new Workflowing(wid,wf,getStoreRuntime())(this)

    // temporary map for Linking    
    var mesh: Map[Exec.ID,Executing] = Map()

    val ee = wf.flow.map(f => {
      spawn(f,wid)      
    })

    val errors = ee.filter{ s => s match {
      case Failure(e) => 
        log.error(s"${w}: ${ee}: ${e}")
        true
      case Success(e) => 
        log.info(s"${w}: ${ee}")
        mesh = mesh + (e.getExecId -> e)
        false
    }}
    
    if(errors.size > 0)
      return Failure(errors.head.failed.get)

    log.info(s"mesh=${mesh}")
    log.info(s"links=${wf.links}")

    // initialize Link vectors
    wf.links.map( link => {
      val from = mesh.get(link.from)
      val to = mesh.get(link.to)
      
      if(! from.isDefined || ! to.isDefined) {
        log.error(s"could not find links: from=${link.from}, to=${link.to}")
        return Failure(new Exception(s"could not find links: from=${link.from}, to=${link.to}"))
      } 
      
      val linking = Linking( wid,
        from = LinkAddr( from.get , link.out),
        to = LinkAddr( to.get , link.in)
      )

      log.info(s"${w}: linking=${linking}")      
    })

    Success(w)
  }

  def spawn(f:Exec,wid:Workflowing.ID):Try[Executing] = {
    log.info(s"spawn: ${f}: wid=${wid}")
    for {
      exec <- registry.resolve(f.typ) match {
        case Some(t) => Success(t)
        case None => 
          Failure(new Exception(s"not resolved: ${f.typ}"))
      }
      executing <- try {
        
        log.info(s"spawning: class=${exec.typ}")
        val cz = Class.forName(exec.typ)

        val args = Array(wid,f.name)
        val argsStr = args.map(_.getClass).toSeq.toString
        cz.getConstructors().find { c => 
          val ctorStr = c.getParameters().map(_.getParameterizedType).toSeq.toString
          val b = argsStr == ctorStr
          log.debug(s"class=${cz}: ctor=${c}: '${argsStr}'=='${ctorStr}': ${b}")
          b
        } match {
          case Some(ctor) => 
            val instance = ctor.newInstance(args:_*)
            log.info(s"'${f.typ}' => ${instance}")
            val e = instance.asInstanceOf[Executing]
            Success(e)
            
          case None => 
            Failure(new Exception(s"constructor not resolved: ${f.typ}: ${cz}"))
        }        
        
      } catch {
        case e:Exception => Failure(e)
      }
    } yield executing
  }

}
