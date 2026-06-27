package io.syspulse.skel.telemetry

import scala.jdk.CollectionConverters._
import scala.util.Random

import io.syspulse.skel.telemetry.parser.TelemetryParser

trait TelemetryDeser {
  def deser(source:Map[String,AnyRef]):TelemetryLike
}

case class Weather(id:Telemetry.ID,ts:Long,temp:Double = 0.0,alt:Double = 0.0) extends TelemetryLike(id,ts)

// object TelemetryParserWeather extends TelemetryParser {

//   override def parse(s:String):Option[Telemetry] = {
//     Some(Weather(id = "W",ts = System.currentTimeMillis))
//   }
// }

// object TelemetryDeserWeather extends TelemetryDeser {
//   override def deser(source:Map[String,AnyRef]):TelemetryLike = 
//     Weather(
//       source("id").asInstanceOf[String], 
//       source("ts").asInstanceOf[Long],
//       source("temp").asInstanceOf[Double],
//       source("alt").asInstanceOf[Double]
//     )
// }
