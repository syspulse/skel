package io.syspulse.skel.telemetry

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

abstract class Telemetry {
    def toLog:String
    def toSimpleLog:String
}