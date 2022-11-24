package io.syspulse.skel.telemetry.parser

import scala.jdk.CollectionConverters._
import scala.util.Random
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.telemetry.{Telemetry,TelemetryLike}

trait TelemetryParser {

  def parse(s:String):Option[Telemetry]
  
  def fromIterator(data:Iterator[String]):Iterator[Telemetry] = {
    data
      .map(_.trim)
      .filter( ! _.isEmpty)
      .flatMap(s => parse(s))
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

  override def parse(s:String):Option[Telemetry] = {
    try {
      s.split(",").map(_.trim).toList match {
        case id :: ts :: data => Some(Telemetry(id,ts.toLong,Map()))
        case _ => {
          log.error(s"failed to parse: '${s}'")
          None
        }
      }
    } catch {
      case e: Exception => log.error(s"failed to parse: '${s}'",e); None
    }
  }
}