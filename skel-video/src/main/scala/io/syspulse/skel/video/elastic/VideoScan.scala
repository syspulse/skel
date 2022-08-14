package io.syspulse.skel.video.elastic

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.elastic.ElasticScan

import io.syspulse.skel.video.Video

trait VideoScan extends ElasticScan[Video] {

  import io.syspulse.skel.video.elastic.VideoElasticJson._
  implicit val fmt = VideoElasticJson.fmt 

  override def getSearchParamas():Map[String,String] = Map(
          "query" -> s""" {"match_all": {}} """,
          "_source" -> """ ["vid", "ts", "title", "category"] """
        )
}