package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.wf.store.WorkflowStateStore

object Executing { 
  case class ID(wid:Workflowing.ID,name:String)
  def id(wid:Workflowing.ID,name:String):ID = ID(wid,name)

}

class Executing(wid:Workflowing.ID,name:String) {
  
  @volatile
  var status:Status = Status.CREATED()

  var stateStore:Option[WorkflowStateStore] = None
  var inputs:Map[String,Linking] = Map()
  var outputs:Map[String,Linking] = Map()

  //override def toString = s"${this.getClass.getName()}(${name},${getStatus},${getInputs},${getOutpus})"
  override def toString = this.getClass.getName()+":"+name+":"+getStatus+":"+getInputs+":"+getOutpus

  // this constructor and init are need for dynamic class instantiation of Executing Executors
  def this() = {
    this(Workflowing.id(),"")
    status = Status.CREATED()
  }

  def init(store:WorkflowStateStore,
           wid:Workflowing.ID,name:String,
           in:Seq[Linking],out:Seq[Linking]):Unit = {
    status match {
      case Status.CREATED() => 
        stateStore = Some(store)
        id = Executing.ID(wid,name)        
        inputs = inputs ++ in.map(link => link.from.let -> link).toMap
        outputs = outputs ++ out.map(link => link.to.let -> link).toMap
        status = Status.INITIALIZED()        

      case Status.INITIALIZED() => 
        log.warn(s"already: ${status}")
      case _ => 
        // already initialized
    }
  }

  var id = Executing.ID(wid,name)  
  val log = Logger(s"${this}-${id}")

  def getExecId = name
  def getId = getRuntimeId
  def getRuntimeId = id
  def getName = name
  def getStatus = status
  def getInputs = inputs.keys
  def getOutpus = outputs.keys
  //def getErrorPolicy = errorPolicy

  def addIn(link:Linking):Executing = {
    inputs = inputs + (link.from.let -> link)
    this
  }

  def addOut(link:Linking):Executing = {
    outputs = outputs + (link.to.let -> link)
    this
  }

  def start(data:ExecData):Try[Status] = {
    log.info(s"start: ${data}")
    status = Status.RUNNING()
    Success(status)
  }

  def stop():Try[Status] = {
    log.info(s"stop")
    status = Status.STOPPED()
    Success(status)
  }

  def broadcast(data:ExecData) = {
    log.info(s"${data}: Broadcast ------------------->>>>> [${outputs.values}]")
    outputs.values.map( linking => {
      linking.input(ExecDataEvent(data))
    })
  }

  def output(out:String,cmd:ExecEvent):Try[Executing] = {
    outputs.get(out) match {
      // case Some(a) if a.isDefined => 
      //   a.get ! cmd
      //   Success(this)
       case Some(linking)  =>
        linking.input(cmd)
        //log.warn(s"no output: ${let}: ${outputs}: ignored")
        Success(this)
      case None => 
        Failure(new Exception(s"output not found: ${out}"))
    }
  }

  def send(out:Let.ID,data:ExecData) = {
    outputs.get(out).map( linking => {
      linking.input(ExecDataEvent(data))
    })
  }

  def exec(in:Let.ID,data:ExecData):Try[ExecData] = {
    // broadcast to all output
    broadcast(data)
    Success(data)
  }

  def onEvent(in:Let.ID,e:ExecEvent):Try[ExecEvent] = {
    log.info(s": ${e} -> [${in}]-${this}")
    e match {
      case ExecDataEvent(d) => 
        val r = exec(in,d).map(d => ExecDataEvent(d))

        if(stateStore.isDefined) {
          //log.info(s">>>>>>>>>>>>>>>>>>>>>>>>>>>>>> COMMITTING: ${r}")
          r match {
            case Success(de1) => 
              stateStore.get.commit(wid,id, de1.data, status = Some("ok"))
            case f => 
              stateStore.get.commit(wid,id, d,status = Some(f.toString))
          }
          
        }

        r
      case e @ ExecCmdStop() =>
        // internal command to stop Runtime
        Success(e)
    }
  }
}
