package io.syspulse.skel.flow

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

class Pipeline[F](name:String,stages:List[Stage[F]] = List(),pipelineDir:String = "/tmp/skel/pipelines") {
  val log = Logger(s"${this}")

  // create directory
  os.makeDir.all(os.Path(pipelineDir))

  def resolveStageLocation(fid:FlowID,stage:Stage[F],fileName:String):String = s"${pipelineDir}/${fid.id}/${stage.getName}/${fileName}"

  def run(data:F):Flow[F] = {
    val fid = FlowID()

    var flow = new Flow(fid,data,this,location = "")
    
    stages.foreach( st => {
      log.info(s"${name}: starting Stage: ${st}")

      val stageLocation = resolveStageLocation(flow.id,st,"")
      flow.location = stageLocation

      os.makeDir.all(os.Path(stageLocation))

      flow = st.start(flow)
    })

    flow = exec(flow)

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

      var err:Boolean = false
      do {
        err = try {
          flow = st.exec(flow)
          false
        }catch {
          case e:Throwable => {
            val errorPolicy = st.getErrorPolicy
            log.error(s"${name}: Stage ${st}: failed: errorPolicy=${errorPolicy}: err=",e)
            
            errorPolicy.repeat
          }
        }
      } while(err)
    })

    flow
  }
}
