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

import io.syspulse.skel.wf.store._

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

class WorkflowEngine(workflowStoreUri:String = "mem://", stateStoreUri:String = "dir:///tmp/skel-wf/runtime", runtime:Runtime, runtimeStoreUri:String = "dir:///tmp/skel-wf/runtime") {
  
  val log = Logger(s"${this}")

  val stateStore = stateStoreUri.split("://").toList match {
    case "dir" :: dir :: Nil => new WorkflowStateStoreDir(dir)
    case "mem" :: Nil => new WorkflowStateStoreMem()
    case _ => new WorkflowStateStoreMem()
  }

  val workflowStore = workflowStoreUri.split("://").toList match {
    //case "dir" :: dir :: Nil => new WorkflowStoreDir(dir)
    case "mem" :: Nil => new WorkflowStoreMem()
    //case _ => new WorkflowStoreMem()
  }

  // only directory is supported
  val runtimeStore = runtimeStoreUri.split("://").toList match {
    case "dir" :: dir :: Nil => dir
  }


  val registry = new WorkflowRegistry(Seq(
    Exec("Log","io.syspulse.skel.wf.exec.LogExec"),
    Exec("Process","io.syspulse.skel.wf.exec.ProcessExec"),
    Exec("Terminate","io.syspulse.skel.wf.exec.TerminateExec"),
    Exec("Cron","io.syspulse.skel.wf.exec.CronExec")
  ))

  def getStoreWorkflow() = workflowStore
  def getStoreState() = stateStore

  def spawn(wf:Workflow):Try[Workflowing] = {
    val wid = Workflowing.id(wf)
    
    // temporary map for Linking    
    var mesh: Map[Exec.ID,Executing] = Map()

    val ee = wf.flow.map(f => {
      spawn(f,wid)      
    })

    val errors = ee.filter{ s => s match {
      case Failure(e) => 
        log.error(s"${wf}: ${ee}: ${e}")
        true
      case Success(e) => 
        log.debug(s"${wf}: ${ee}")
        mesh = mesh + (e.getExecId -> e)
        false
    }}
    
    if(errors.size > 0)
      return Failure(errors.head.failed.get)

    log.debug(s"mesh=${mesh}")
    log.debug(s"links=${wf.links}")

    // initialize Link vectors
    val ll = wf.links.map( link => {
      val from = mesh.get(link.from)
      val to = mesh.get(link.to)
      
      if(! from.isDefined || ! to.isDefined) {
        log.error(s"could not find links: from=${link.from}, to=${link.to}")
        return Failure(new Exception(s"could not find links: from=${link.from}, to=${link.to}"))
      } 
      
      val linking = Linking( 
        from = LinkAddr( from.get , link.out),
        to = LinkAddr( to.get , link.in)
      )
      
      log.debug(s"${wf}: linking=${linking}")

      from.get.addOut(linking)
      to.get.addIn(linking)

      linking
    })

    // create Runtime
    val llr = ll.map(linking => { 

      runtime.spawn(linking) match {
        case Failure(e) => 
          return Failure(e)
        case Success(linkingRun) => 
          linking.bind(linkingRun)
          log.debug(s"${wf}: linking=${linking}: runtime=${linkingRun}")
          linkingRun
      }
    })

    // add to state store
    // val ws = WorkflowState(wid,Seq(),WorkflowState.STATUS_INIT)
    // getStoreState().+(ws)

    val workflowing = new Workflowing(wid,wf,getStoreState(),mesh,ll,llr)(this)
    workflowing.init()

    // init 
    ee.flatMap(_.toOption).map( e =>
      e.init(getStoreState(), workflowing, wid, e.getName, Seq(), Seq()) 
    )

    Success(workflowing)
  }

  def spawn(f:Exec,wid:Workflowing.ID):Try[Executing] = {
    log.debug(s"spawn: ${f}: wid=${wid}")
    for {
      exec <- registry.resolve(f.typ) match {
        case Some(t) => Success(t)
        case None => 
          Failure(new Exception(s"not resolved: ${f.typ}"))
      }
      executing <- try {
        
        log.debug(s"spawning: class=${exec.typ}")
        val cz = Class.forName(exec.typ)

        val args = Array(wid,f.name,f.data.getOrElse(Map.empty[String,Any]))
        val argsStr = args.map(_.getClass).toSeq
        cz.getConstructors().find { c => 
          val ctorStr = c.getParameters().map(_.getParameterizedType).toSeq
          // ATTENTION: expecting 3 parameters !
          val b = ctorStr.size == 3//argsStr.toString == ctorStr.toString
          log.debug(s"class=${cz}: ctor=${c}: '${argsStr.toString}'=='${ctorStr.toString}': ${b}")
          b
        } match {
          case Some(ctor) => 
            val instance = ctor.newInstance(args:_*)
            log.debug(s"'${f.typ}' => ${instance}")
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

  def start(wf:Workflowing):Try[Workflowing] = {
    log.info(s"start: ${wf}")

    // start Executings with initial default data
    val wfRuntimeDir = runtimeStore + "/" + wf.getId
    os.makeDir.all(os.Path(wfRuntimeDir,os.pwd))

    // ingest additional metadata
    val data = ExecData(wf.data.attr ++ Map( "data_dir" -> wfRuntimeDir))
    
    // start all Executing with Workflow global data !
    wf.getExecs.map( e => e.start(data) )

    // start Running infra
    wf.getRunning.map( r => r.start())

    wf.start()

    Success(wf)
  }

  def stop(wf:Workflowing):Try[Workflowing] = {
    log.info(s"stop: ${wf}")

    // stop Executings with initial data
    wf.getExecs.map( e => e.stop())

    // stop running infra
    wf.getRunning.map( r => r.stop())

    wf.stop()

    Success(wf)
  }

}
