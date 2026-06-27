package io.syspulse.skel.telemetry.parser

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import scala.util.Random
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.telemetry.{Telemetry,TelemetryLike}

trait TelemetryParser {

  def parse(s:String):Try[Telemetry]
  
  def fromIterator(data:Iterator[String]):Iterator[Telemetry] = {
    data
      .map(_.trim)
      .filter( ! _.isEmpty)
      .flatMap(s => parse(s).toOption)
  }

  def fromFile(file:String) = {
    fromIterator(scala.io.Source.fromFile(file).getLines())
  }

  def fromSeq(data:Seq[String]) = {
    fromIterator(data.iterator)
  }

  def fromString(data:String) = {
    fromIterator(data.split("\\n").iterator)
  }
}

object TelemetryParserDefault extends TelemetryParser {
  private val log = Logger(s"${this}")

  override def parse(s:String):Try[Telemetry] = {
    try {
      s.split(",",-1).map(_.trim).toList match {
        case id :: ts :: data => 
          Success(Telemetry(id,ts.toLong,data))
        case _ => {
          log.error(s"failed to parse: '${s}'")
          Failure(new Exception(s"failed to parse: '${s}'"))
        }
      }
    } catch {
      case e: Exception => 
        Failure(new Exception(s"failed to parse: '${s}'", e))        
    }
  }
}