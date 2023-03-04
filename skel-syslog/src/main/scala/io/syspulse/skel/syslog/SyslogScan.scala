package io.syspulse.skel.syslog

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.elastic.ElasticScan

trait SyslogScan extends ElasticScan[Syslog] {

  import io.syspulse.skel.syslog.elastic.SyslogElasticJson
  import io.syspulse.skel.syslog.elastic.SyslogElasticJson._
  implicit val fmt = SyslogElasticJson.fmt 

  override def getSearchParamas():Map[String,String] = Map(
          "query" -> s""" {"match_all": {}} """,
          "_source" -> """ ["ts", "level", "area", "text"] """
        )
}