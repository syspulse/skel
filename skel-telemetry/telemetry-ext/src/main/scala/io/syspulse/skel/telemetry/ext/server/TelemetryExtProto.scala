package io.syspulse.skel.telemetry.ext.server

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.telemetry.ext.TelemetryChain

final case class TelemetryChainRes(status:String,telemetry: Option[TelemetryChain])
