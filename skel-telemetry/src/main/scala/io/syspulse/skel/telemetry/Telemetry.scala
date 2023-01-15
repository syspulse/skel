package io.syspulse.skel.telemetry

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.syspulse.skel.Ingestable

abstract class TelemetryLike(id:Telemetry.ID, ts:Long = System.currentTimeMillis)

case class Telemetry(id:Telemetry.ID, ts:Long = System.currentTimeMillis, v:List[Any]) extends TelemetryLike(id,ts) with Ingestable {
  def data = v
}

object Telemetry {
  type ID = String
}

