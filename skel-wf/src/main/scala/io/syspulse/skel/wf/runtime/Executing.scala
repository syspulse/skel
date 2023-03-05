package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

object Executing { 
  case class ID(wid:Workflowing.ID,name:String)
  def id(wid:Workflowing.ID,name:String):ID = ID(wid,name)

}

class Executing(wid:Workflowing.ID,name:String) {
  
  @volatile
  var status:Status = Status.CREATED()
  // var inputs:Map[String,ActorRef[ExecEvent]] = Map()
  // var outputs:Map[String,Option[ActorRef[ExecEvent]]] = Map()
  var inputs:Map[String,Linking] = Map()
  var outputs:Map[String,Linking] = Map()

  def output(let:String,cmd:ExecEvent):Try[Executing] = {
    outputs.get(let) match {
      // case Some(a) if a.isDefined => 
      //   a.get ! cmd
      //   Success(this)
       case Some(a)  =>
        a ! cmd
        //log.warn(s"no output: ${let}: ${outputs}: ignored")
        Success(this)
      case None => 
        Failure(new Exception(s"output not found: ${let}"))
    }
  }

  // this constructor and init are need for dynamic class instantiation of Executing Executors
  def this() = {
    this(Workflowing.id(),"")
    status = Status.CREATED()
  }

  def init(wid:Workflowing.ID,name:String,
           in:Seq[Linking],out:Seq[Linking]):Unit = {
    status match {
      case Status.CREATED() => 
        id = Executing.ID(wid,name)
        status = Status.INITIALIZED()
        inputs = in.map(link => link.from.let -> link).toMap
        outputs = out.map(link => link.to.let -> link).toMap
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
  //def getErrorPolicy = errorPolicy

  def start(data:ExecData):Try[Status] = {
    log.info(s"start: ${data}")
    Success(Status.STARTED())
  }

  def stop():Try[Status] = {
    log.info(s"stop")
    Success(Status.STOPPED())
  }

  def onEvent(e:ExecEvent):Try[ExecEvent] = {
    Success(e)
  }
}
