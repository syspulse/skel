package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.exec
import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.registy.WorkflowRegistry

class WorkflowEngine(store:String = "dir:///tmp/skel-wf") {
  val log = Logger(s"${this}")

  val storeWorkflow = s"${store}/workflow"  
  val storeRuntime = s"${store}/runtime"

  val registry = new WorkflowRegistry(Seq(
    Flowlet("Log","io.syspulse.skel.wf.exe.Log")
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

    log.info(s"${w}: ${ss}")
    Success(w)
  }

  def spawn(f:Flowlet,wid:Workflowing.ID):Try[Status] = {
    log.info(s"spawn: ${f}: wid=${wid}")
    for {
      flowlet <- registry.resolve(f.typ) match {
        case Some(t) => Success(t)
        case None => Failure(new Exception(s"not resolved: ${f.typ}"))
      }
      flowing <- try {
        //val e = new exec.LogExec(wid,flowlet.name)
        val cz = Class.forName(flowlet.name)

        val args = Seq(wid,flowlet.name)
        cz.getConstructors().find { c => 
          c.getParameters().map(_.getParameterizedType) == args.map(_.getClass)
        } match {
          case Some(ctor) => 
            val instance = ctor.newInstance(args)
            log.info(s"${f.typ} ===> ${instance}")
            val e = instance.asInstanceOf[Flowing]
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
