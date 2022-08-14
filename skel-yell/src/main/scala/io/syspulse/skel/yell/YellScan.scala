package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.elastic.ElasticScan

trait YellScan extends ElasticScan[Yell] {

  import io.syspulse.skel.yell.elastic.YellElasticJson
  import io.syspulse.skel.yell.elastic.YellElasticJson._
  implicit val fmt = YellElasticJson.fmt 

  override def getSearchParamas():Map[String,String] = Map(
          "query" -> s""" {"match_all": {}} """,
          "_source" -> """ ["ts", "level", "area", "text"] """
        )
}