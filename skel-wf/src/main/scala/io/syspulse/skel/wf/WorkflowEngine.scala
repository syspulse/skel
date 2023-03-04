package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.registy.WorkflowRegistry

class WorkflowEngine(store:String = "dir:///tmp/skel-wf") {
  val log = Logger(s"${this}")

  val storeWorkflow = s"${store}/workflow"  
  val storeRuntime = s"${store}/runtime"

  val registry = new WorkflowRegistry(Seq(
    Exec("Log","io.syspulse.skel.wf.exec.LogExec")
  ))

  // create stores
  os.makeDir.all(os.Path(s"${storeWorkflow}",os.pwd))
  os.makeDir.all(os.Path(s"${storeRuntime}",os.pwd))

  def getStoreRuntime() = storeRuntime
  def getStoreWorkflow() = storeWorkflow

  def spawn(wf:Workflow):Try[Workflowing] = {
    val wid = Workflowing.id(wf)
    val w = new Workflowing(wid,wf,getStoreRuntime())(this)
    
    val ss = wf.flow.map(f => {
      val status = spawn(f,wid)
      status
    })

    val ssError = ss.filter{ s => s match {
      case Failure(e) => 
        log.error(s"${w}: ${ss}: ${e}")
        true
      case _ => log.info(s"${w}: ${ss}")
        false
    }}    
    
    if(ssError.size > 0)
      Failure(ssError.head.failed.get)
    else
      Success(w)
  }

  def spawn(f:Exec,wid:Workflowing.ID):Try[Status] = {
    log.info(s"spawn: ${f}: wid=${wid}")
    for {
      flowlet <- registry.resolve(f.typ) match {
        case Some(t) => Success(t)
        case None => Failure(new Exception(s"not resolved: ${f.typ}"))
      }
      flowing <- try {
        
        log.info(s"spawning: class=${flowlet.typ}")
        val cz = Class.forName(flowlet.typ)

        val args = Array(wid,flowlet.name)
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
            Success(e.status)
          case None => 
            Failure(new Exception(s"constructor not resolved: ${f.typ}: ${cz}"))
        }        
        
      } catch {
        case e:Exception => Failure(e)
      }
    } yield flowing
  }

}
