package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

object Executing {
  case class ID(wid:Workflowing.ID,name:String)

  def id(wid:Workflowing.ID,name:String):ID = ID(wid,name)
}

class Executing(wid:Workflowing.ID,name:String)(implicit errorPolicy:ErrorPolicy = new RepeatErrorPolicy()) {
  
  @volatile
  var status:Status = Status.INITIALIZED()

  // this constructor and init are need for dynamic class instantiation of Executing Executors
  def this() = {
    this(Workflowing.id(),"")
    status = Status.CREATED()
  }
  def init(wid:Workflowing.ID,name:String):Unit = {
    status match {
      case Status.CREATED() => 
        id = Executing.ID(wid,name)
        status = Status.INITIALIZED()
      case _ => 
        // already initialized
    }
  }

  var id = init(wid,name)
  val log = Logger(s"${this}-${id}")
  

  def getId = id
  def getName = name
  def getErrorPolicy = errorPolicy

  def start(data:ExecData):Try[Status] = {
    log.info(s"data=${data}")
    Success(Status.STARTED())
  }

  def stop():Try[Status] = {
    Success(
      Status.STOPPED()
    )
  }
}
