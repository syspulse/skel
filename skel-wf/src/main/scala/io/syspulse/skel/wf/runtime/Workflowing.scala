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

object Workflowing {
  case class ID(wid:Workflow.ID,ts:Long) {
    override def toString = s"${wid}-${ts}"
  }

  def id(name:String,ts:Long = System.currentTimeMillis):ID = Workflowing.ID(name,ts)
  def id(wf: Workflow):ID = id(wf.id,System.currentTimeMillis)
  def id():ID = id("",0L)
}

class Workflowing(id:Workflowing.ID,wf:Workflow,stateStore:String,mesh:Map[Exec.ID,Executing],links:Seq[Linking],running:Seq[Running])(implicit engine:WorkflowEngine) {
  val log = Logger(s"${id}")

  override def toString() = s"Workflowing(${id})[${mesh},${links}]"

  var data:ExecData = wf.attributes
  val stateLoc = s"${stateStore}/${id.toString}"

  // create directory
  os.makeDir.all(os.Path(stateLoc,os.pwd))

  log.info(s"state=${stateLoc}: wf=${wf}: data=${data}")

  def getMesh = mesh
  def getExecs = mesh.values
  def getLinks = links
  def getRunning = running

  def emit(execName:String,input:Let.ID,event:ExecEvent):Try[Workflowing] = {
    mesh.get(execName) match {
      case Some(e) => 
        val in = e.inputs.get(input)

        in match {          
          case Some(linking) => 
            val r =  linking.input(event)
            Success(this)

          case None =>
            // link is not found, so emit synchronously into input directly
            e.onEvent(input,event).map(_ => this)
            //Failure(new Exception(s"not found: ${execName}:${input}"))
        }        
      case None => 
        Failure(new Exception(s"not found: ${execName}"))
    }
  }
}
