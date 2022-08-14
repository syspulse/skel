package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._
import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic._

trait YellSearch extends ElasticSearch[Yell] {

  import io.syspulse.skel.yell.YellElasticJson._
  implicit val fmt = YellElasticJson.fmt 

  def getWildcards(txt:String) = s"""
    { 
      "query_string": {
        "query": "${txt}",
        "fields": ["area", "text"]
      }
    }
    """

  def getSearches(txt:String) = s"""
    { "multi_match": { "query": "${txt}", "fields": [ "area", "text" ] }}
    """

  def getSearch(txt:String) = s"""
    { "match": { "text": "${txt}" }}
    """
}