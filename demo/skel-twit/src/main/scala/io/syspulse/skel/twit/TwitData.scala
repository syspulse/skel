package io.syspulse.skel.twit

import scala.jdk.CollectionConverters._

import io.syspulse.skel.ingest.Ingestable
import io.syspulse.skel.util.Util

case class TwitData(id: Long,ts:Long, user:String, txt: String) extends Ingestable {
  def toLog = s"${Util.tsToString(ts)}: ${id}: user=${user},text=${txt}"
  def toSimpleLog = s"${Util.tsToString(ts)},${id},${user},${txt}"
}