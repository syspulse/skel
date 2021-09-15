package io.syspulse.ekm

import scala.jdk.CollectionConverters._

import io.syspulse.skel.ingest.Ingestable
import io.syspulse.skel.util.Util

case class EkmTelemetry(device:String="",ts:Long=System.currentTimeMillis(),kwhTotal:Double = 0.0, v1:Double = 0.0,v2:Double=0.0,v3:Double=0.0,w1:Double=0.0,w2:Double=0.0,w3:Double=0.0) extends Ingestable {
    def toLog = s"${Util.tsToString(ts)}: ${device}: kwh=${kwhTotal},v1=${v1},v2=${v2},v3=${v3},w1=${w1},w2=${w2},w3=${w3}"
    def toSimpleLog = s"${Util.tsToString(ts)},${device},${kwhTotal},${v1},${v2},${v3},${w1},${w2},${w3}"
}