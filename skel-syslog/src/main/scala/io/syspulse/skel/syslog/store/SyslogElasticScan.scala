package io.syspulse.skel.syslog.store

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.elastic.ElasticScan
import io.syspulse.skel.syslog.Syslog

trait SyslogElasticScan extends ElasticScan[Syslog] {

  import io.syspulse.skel.syslog.elastic.SyslogElasticJson
  import io.syspulse.skel.syslog.elastic.SyslogElasticJson._
  implicit val fmt = SyslogElasticJson.fmt 

  override def getSearchParamas():Map[String,String] = Map(
          "query" -> s""" {"match_all": {}} """,
          "_source" -> """ ["ts", "level", "area", "text"] """
        )
}