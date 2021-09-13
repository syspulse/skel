package io.syspulse.skel.scrap.npp

import os._

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import scala.util.Random

import com.typesafe.scalalogging.Logger

import os._
import upickle._
import upickle.default._

// Pipeline(Name):    Stage     ->     Stage     ->     Stage
// Flow(id=1):         [FlowData] ->    FlowData[]
// Flow(id=2):         [FlowData] ->    FlowData[]

case class Flow[F](id:FlowID,data:F,pipeline:Pipeline[F],var location:String)

class Pipeline[F](name:String,stages:List[Stage[F]] = List(),pipelineDir:String = "/tmp/skel/pipelines") {
  val log = Logger(s"${this}")

  // create directory
  os.makeDir.all(os.Path(pipelineDir))

  def resolveStageLocation(fid:FlowID,stage:Stage[F],fileName:String):String = s"${pipelineDir}/${fid.id}/${stage.getName}/${fileName}"

  def run(data:F):Flow[F] = {
    val fid = FlowID()
    //val fidLocation = s"${pipelineDir}/${fid.id}"
    //os.makeDir.all(os.Path(fidLocation))

    var flow = new Flow(fid,data,this,location = "")
    
    stages.foreach( st => {
      log.info(s"${name}: starting Stage: ${st}")

      val stageLocation = resolveStageLocation(flow.id,st,"")
      flow.location = stageLocation

      os.makeDir.all(os.Path(stageLocation))

      flow = st.start(flow)
    })

    exec(flow)

    stages.foreach( st => {
      log.info(s"${name}: stopping Stage: ${st}")
      
      val stageLocation = resolveStageLocation(flow.id,st,"")
      flow.location = stageLocation

      flow = st.stop(flow)
    })

    flow
  }

  protected def exec(flow0:Flow[F]):Flow[F] = {
    var flow = flow0
    stages.foreach( st => {
      log.info(s"${name}: executing Stage: ${st}")

      val stageLocation = resolveStageLocation(flow.id,st,"")
      flow.location = stageLocation

      flow = st.exec(flow)
    })

    flow
  }
}

case class StageID(id:String)
case class FlowID(id:String)

object StageID {

  def apply(name:String,ts:Long):StageID = new StageID(s"${if(name.isBlank) "" else s"${name}-"}${ts.toString}")
  def apply(name:String=""):StageID = apply(name,System.currentTimeMillis)
}

object FlowID {

  def apply(name:String,ts:Long):FlowID = new FlowID(s"${if(name.isBlank) "" else s"${name}-"}${ts.toString}")
  def apply(name:String=""):FlowID = apply(name,System.currentTimeMillis)
}


abstract class Stage[F](name:String) {
  val log = Logger(s"${this}")

  def getName = name

  def exec(flow:Flow[F]):Flow[F]
  def start(flow:Flow[F]):Flow[F] = { flow }
  def stop(flow:Flow[F]):Flow[F] = { flow }
}

