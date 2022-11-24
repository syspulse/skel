package io.syspulse.skel.telemetry

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.syspulse.skel.Ingestable

abstract class TelemetryLike(id:Telemetry.ID, ts:Long = System.currentTimeMillis)

case class Telemetry(id:Telemetry.ID, ts:Long = System.currentTimeMillis, data:List[Any]) extends TelemetryLike(id,ts) with Ingestable

// trait Telemetry extends Ingestable { 
//   def id:Telemetry.ID
//   def ts:Long = System.currentTimeMillis
//   //def data:D
//   override def toLog:String = toString
//   override def getKey:Option[Any] = Some(id)

//   def deser(source:Map[String,AnyRef]):Telemetry
// }

object Telemetry {
  type ID = String
}

