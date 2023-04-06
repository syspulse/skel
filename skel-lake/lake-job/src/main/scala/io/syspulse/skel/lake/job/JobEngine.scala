package io.syspulse.skel.lake.job

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

trait JobEngine {  
  def all():Try[Seq[Job]]
  def ask(job:Job):Try[Job]
  def get(job:Job):Try[Job]
  def create(name:String,inputs:Map[String,String]=Map()):Try[Job]
  def del(job:Job):Try[Job]
  def run(job:Job,script:String):Try[Job]
}

object JobEngine {
  val log = Logger(s"${this}")

  def decodeData(data:List[String],confFilter:(String) => Boolean) = {
    data
      .filter(!_.trim.isEmpty)
      .filter(d => confFilter(d.trim))
      .map(d => {
        if(d.startsWith("file://")) {
          val code = os.read(os.Path(d.stripPrefix("file://"),os.pwd))
          code -> ""
        } else {
          d.split("=").toList match {
            case k :: v :: Nil => k -> v
            case _ => d -> ""
          }
        }
      })
      .toMap
  }

  def dataToVars(data:List[String]) = decodeData(data,(d) => {! d.startsWith("spark.")}) 

  def dataToConf(data:List[String]) = decodeData(data,(d) => { d.startsWith("spark.")})           

  def pipeline(engine:JobEngine,name:String,script:String,data:List[String] = List(),poll:Long = 5000L) = {
    
    // create source block with all expected variables
    var src0 = dataToVars(data).map( _ match {
      case(code,"") =>
        code
      case(name,value) =>
        s"${name} = ${value}"
    }).mkString("\n")

    val src = src0 + "\n" +
      os.read(os.Path(script.stripPrefix("file://"),os.pwd))

    log.info(s"src=${src}")
    
    for {
      j1 <- engine.create(name,dataToConf(data))

      j2 <- {
        var j:Try[Job] = engine.get(j1)
        while(j.isSuccess && j.get.state == "starting") {                  
          Thread.sleep(poll)
          j = engine.get(j1)
        } 
        j
      }
      
      j3 <- {
        engine.run(j2,src)
      }

      j4 <- {
        var j:Try[Job] = engine.ask(j3)

        while(j.isSuccess && j.get.state != "available") {
          Thread.sleep(poll)
          j = engine.ask(j3)
        } 
        j
      }
      j5 <- {
        j4.result match {
          case Some("error") => 
            log.error(s"Job: Error=${j4.output.getOrElse("")}")
          case Some("ok") =>
            log.info(s"Job: OK=${j4.output.getOrElse("")}")
          case _ => 
            log.info(s"Job: Unknown=${j4.result}: output=${j4.output.getOrElse("")}")
        }              

        engine.del(j4)
      }
    } yield j4
  }
}