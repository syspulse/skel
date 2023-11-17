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
import io.syspulse.skel.wf.registry.WorkflowRegistry

import io.syspulse.skel.wf.store._

object WorkflowEngine {
  val DATA_DIR = "data.dir"
}

class WorkflowEngine(workflowStore:WorkflowStore, stateStore:WorkflowStateStore, runtime:Runtime, runtimeStoreUri:String = "dir://store/runtime")
    (implicit registry:WorkflowRegistry) {
  
  val log = Logger(s"${this}")

  // only directory is supported
  val runtimeStore = runtimeStoreUri.split("://").toList match {
    case "dir" :: dir :: Nil => dir    
  }
  
  def getStoreWorkflow() = workflowStore
  def getStoreState() = stateStore

  def remove(wf:Workflowing):Try[WorkflowEngine] = {
    remove(wf.getId)
  }

  def remove(id:Workflowing.ID):Try[WorkflowEngine] = {
    log.info(s"remove: ${id}")
    for {
      _ <- stateStore.del(id)
      r <- deleteDataDir(id)
    } yield r
  }

  def respawn(id:Workflowing.ID):Try[Workflowing] = {
    for {
      st <- stateStore.?(id)
      wf <- workflowStore.?(st.wid)
      w <- spawn(wf,Some(id))
    } yield w
  }

  def spawn(wf:Workflow,wid0:Option[Workflowing.ID] = None):Try[Workflowing] = {
    val wid = wid0.getOrElse(Workflowing.id(wf))
    
    // temporary map for Linking    
    var mesh: Map[Exec.ID,Executing] = Map()

    val ee = wf.execs.map(f => {
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

  def createDataDir(id:Workflowing.ID):Try[WorkflowEngine] = {
    val wfRuntimeDir = runtimeStore + "/" + id
    try {
      os.makeDir.all(os.Path(wfRuntimeDir,os.pwd))
      Success(this)
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def deleteDataDir(id:Workflowing.ID):Try[WorkflowEngine] = {
    val wfRuntimeDir = runtimeStore + "/" + id
    try {      
      os.remove.all(os.Path(wfRuntimeDir,os.pwd))
      Success(this)
    } catch {
      case e:Exception => Failure(e)
    }
  }


  def start(wf:Workflowing):Try[Workflowing] = {
    log.info(s"start: ${wf}")
    
    val wfRuntimeDir = runtimeStore + "/" + wf.getId

    // os.makeDir.all(os.Path(wfRuntimeDir,os.pwd))
    createDataDir(wf.getId)

    // ingest additional metadata
    val data = ExecData(wf.data.attr ++ Map( WorkflowEngine.DATA_DIR -> wfRuntimeDir))
    
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
