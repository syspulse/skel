package io.syspulse.skel.telemetry.ext

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.Ingestable
import com.typesafe.scalalogging.Logger

case class TelemetryExt(
  sys: Boolean,
  sysEventSubject: String,
  data: TelemetryChain,
  
) extends Ingestable

object TelemetryExt {
  val BLOCKCHAIN_KEY = "blockchain"
}
