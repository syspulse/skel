package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class IngestData(device:String="",ts:Long=System.currentTimeMillis(),data:String) extends Ingestable {
    override def toLog = s"${Util.tsToString(ts)}: ${device}: data=${data}"
    override def toSimpleLog = s"${Util.tsToString(ts)},${device},${data}"
}