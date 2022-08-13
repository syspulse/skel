package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.elastic._

class YellScan extends ElasticScan[Yell] {

  import io.syspulse.skel.yell.YellElasticJson._
  implicit val fmt = YellElasticJson.fmt 

  override def getSearchParamas():Map[String,String] = Map(
          "query" -> s""" {"match_all": {}} """,
          "_source" -> """ ["vid", "ts", "title", "category"] """
        )
}