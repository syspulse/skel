package io.syspulse.skel.wf.runtime

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import scala.util.Random

import os._
import upickle._
import upickle.default._

import io.syspulse.skel.wf._

// Pipeline(Name):    Stage     ->     Stage     ->     Stage
// Flow(id=1):         [FlowData] ->    FlowData[]
// Flow(id=2):         [FlowData] ->    FlowData[]

object Workflowing {
  case class ID(wid:Workflow.ID,ts:Long) {
    override def toString = s"${wid}-${ts}"
  }

  def id(name:String,ts:Long = System.currentTimeMillis):ID = Workflowing.ID(name,ts)
  def id(wf: Workflow):ID = id(wf.id,System.currentTimeMillis)
  def id():ID = id("",0L)
}

class Workflowing(id:Workflowing.ID,wf:Workflow,stateStore:String)(implicit engine:WorkflowEngine) {
  val log = Logger(s"${id}")

  override def toString() = s"Workflowing(${id})"

  var data:ExecData = wf.attributes
  val stateLoc = s"${stateStore}/${id.toString}"

  // create directory
  os.makeDir.all(os.Path(stateLoc,os.pwd))

  log.info(s"state=${stateLoc}: wf=${wf}: data=${data}")

  def start(data0:ExecData):Seq[Try[Executing]] = {
    
    val rr = wf.flow.map( f => {
      log.info(s"starting: ${f}")
      val r = engine.spawn(f,id)
      log.info(s"${f}: ${r}")
      r
    })    
    rr
  }
}
