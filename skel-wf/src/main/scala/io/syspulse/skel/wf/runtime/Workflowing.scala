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
import io.syspulse.skel.wf.store.WorkflowStateStore

object Workflowing {
  // case class ID(wid:Workflow.ID,ts:Long) {
  //   override def toString = s"${wid}-${ts}"
  // }
  type ID = String
  def apply(wid:Workflow.ID,ts:Long):ID = s"${wid}-${ts}"

  def id(name:String,ts:Long = System.currentTimeMillis):ID = apply(name,ts)//Workflowing.ID(name,ts)
  def id(wf: Workflow):ID = id(wf.id,System.currentTimeMillis)
  def id():ID = id("",0L)
}

class Workflowing(
  id:Workflowing.ID,
  wf:Workflow,
  stateStore:WorkflowStateStore,
  mesh:Map[Exec.ID,Executing],
  links:Seq[Linking],
  running:Seq[Running])(implicit engine:WorkflowEngine) {
  
  val log = Logger(s"${id}")

  override def toString() = s"Workflowing(${id})[${mesh},${links}]"

  @volatile
  var state:WorkflowState = WorkflowState(id,wf.id,WorkflowState.STATUS_CREATED)
  var data:ExecData = ExecData(wf.data)
  
  log.info(s"store=${stateStore}: wf=${wf}: data=${data}")

  def getId = id
  def getMesh = mesh
  def getExecs = mesh.values
  def getLinks = links
  def getRunning = running

  def init():Try[WorkflowState] = {
    state = 
      //stateStore.+(state).map(_ => state)    
      stateStore.?(id) match {
        case Success(state1) => 
          state1
        case Failure(e) => // not found, leave as initialized
          WorkflowState(id,wf.id,WorkflowState.STATUS_INITIALIZED)    
      } 

    log.info(s"init: ${state}")
    Success(state)
  }

  def start():Try[WorkflowState] = {
    log.info(s"start: ${state}")
    stateStore.update(id,status = Some(WorkflowState.STATUS_RUNNING)).map(state1 => {
      state = state1
      state
    })
  }

  def stop():Try[WorkflowState] = {
    log.info(s"stop: ${state}")
    stateStore.update(id,status = Some(WorkflowState.STATUS_STOPPED)).map(state1 => {
      state = state1
      state
    })
  }

  def terminate():Try[WorkflowState] = {
    log.warn(s"terminating: ${id}(${state})")
    engine.stop(this).map( _ => state)
  }

  def emit(execName:String,input:Let.ID,event:ExecEvent):Try[Workflowing] = {
    
    mesh.get(execName) match {
      case Some(e) => 
        // if event is DataEvent, inject state
        val event1 = event match {
          case ExecDataEvent(data) =>
              val attr = { 
                if(state.states.size == 0) Map[String,Any]() else {
                  state.states.filter(_.eid == e.id).lastOption match {
                    case Some(es) => es.data.attr
                    case None => Map[String,Any]()
                  }
                }
              } ++ data.attr
              ExecDataEvent(ExecData(attr))
          case _ => event
        }
        
        val in = e.inputs.get(input)

        in match {          
          case Some(linking) => 
            val r =  linking.input(event1)
            Success(this)

          case None =>
            // link is not found, so emit synchronously into input directly
            e.onEvent(input,event1).map(_ => this)
            //Failure(new Exception(s"not found: ${execName}:${input}"))
        }        
      case None => 
        Failure(new Exception(s"not found: ${execName}"))
    }
  }
}
