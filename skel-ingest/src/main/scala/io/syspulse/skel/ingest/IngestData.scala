package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._

import io.syspulse.skel.ingest.Ingestable
import io.syspulse.skel.util.Util

case class IngestData(device:String="",ts:Long=System.currentTimeMillis(),data:String) extends Ingestable {
    def toLog = s"${Util.tsToString(ts)}: ${device}: data=${data}"
    def toSimpleLog = s"${Util.tsToString(ts)},${device},${data}"
}