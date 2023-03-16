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
  //case class ID(wid:Workflowing.ID,name:String)
  type ID = String
  def apply(wid:Workflowing.ID,name:String):ID = s"${name}:${wid}"
  def id(wid:Workflowing.ID,name:String):ID = apply(wid,name) //ID(wid,name)
}

// data0 - initial preconfigured data from Exec 
class Executing(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) {
  
  @volatile
  var status:Status = Status.CREATED()

  var stateStore:Option[WorkflowStateStore] = None
  var inputs:Map[String,Linking] = Map()
  var outputs:Map[String,Linking] = Map()
  
  // data during execution
  var dataWorkflow = ExecData(Map())
  var workflowing:Option[Workflowing] = None

  //override def toString = s"${this.getClass.getName()}(${name},${getStatus},${getInputs},${getOutpus})"
  override def toString = this.getClass.getName()+":"+name+":"+getStatus+":"+getInputs+":"+getOutpus

  // this constructor and init are need for dynamic class instantiation of Executing Executors
  def this() = {
    this(Workflowing.id(),"",Map.empty)
    status = Status.CREATED()
  }

  def init(store:WorkflowStateStore,
           workflowing:Workflowing,
           wid:Workflowing.ID,name:String,
           in:Seq[Linking],out:Seq[Linking]):Unit = {
    status match {
      case Status.CREATED() => 
        stateStore = Some(store)
        this.workflowing = Some(workflowing)
        id = Executing.id(wid,name)        
        inputs = inputs ++ in.map(link => link.from.let -> link).toMap
        outputs = outputs ++ out.map(link => link.to.let -> link).toMap
        status = Status.INITIALIZED()        

      case Status.INITIALIZED() => 
        log.warn(s"already: ${status}")
      case _ => 
        // already initialized
    }
  }

  var id = Executing.id(wid,name)
  val log = Logger(this.getClass.getName()+":"+name)

  def getExecId = name
  def getId = getRuntimeId
  def getRuntimeId = id
  def getName = name
  def getStatus = status
  def getInputs = inputs.keys
  def getOutpus = outputs.keys
  //def getData = dataWorkflow.attr
  //def getErrorPolicy = errorPolicy

  def addIn(link:Linking):Executing = {
    //inputs = inputs + (link.from.let -> link)
    inputs = inputs + (link.to.let -> link)
    this
  }

  def addOut(link:Linking):Executing = {    
    //outputs = outputs + (link.to.let -> link)
    outputs = outputs + (link.from.let -> link)
    this
  }

  def start(dataWorkflow:ExecData):Try[Status] = {
    log.info(s"start: ${dataWorkflow}")
    this.dataWorkflow = dataWorkflow
    status = Status.RUNNING()
    Success(status)
  }

  def stop():Try[Status] = {
    log.info(s"stop")
    status = Status.STOPPED()
    Success(status)
  }

  def broadcast(data:ExecData) = {
    log.debug(s"${data}: Broadcast >>> [${outputs.values}]")
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

  def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    // broadcast to all output
    broadcast(data)
    Success(ExecDataEvent(data))
  }

  def onEvent(in:Let.ID,e:ExecEvent):Try[ExecEvent] = {
    log.debug(s": ${e} -> [${in}]-${this}")
    e match {
      case ExecDataEvent(d) => 
        val r = exec(in,ExecData(d.attr))

        if(stateStore.isDefined) {
          r match {
            case Success(e1) => 
              e1 match {
                case ExecDataEvent(data1) =>
                  stateStore.get.commit(wid,id, data1, status = Some("ok"))
                case ExecCmdStop(who) =>
                  // Exec asks to stop
                  stateStore.get.commit(wid,id, ExecData(Map()), status = Some("stop"))
                  // signal termination
                  workflowing.get.terminate()
              }
            case f => 
              stateStore.get.commit(wid,id, d,status = Some(f.toString))
          }
          
        } else {
          log.error(s"stateStore undefined: event=${r}")
        }

        r
      case e @ ExecCmdStop(who) =>
        // internal command to stop Runtime
        Success(e)
    }
  }
}
