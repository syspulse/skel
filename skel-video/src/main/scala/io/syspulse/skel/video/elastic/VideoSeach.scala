package io.syspulse.skel.video.elastic

import scala.jdk.CollectionConverters._
import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic.ElasticSearch

import io.syspulse.skel.video.Video

trait VideoSearch extends ElasticSearch[Video] {

  import io.syspulse.skel.video.elastic.VideoElasticJson._
  implicit val fmt = VideoElasticJson.fmt 

  def getWildcards(txt:String) = s"""
    { 
      "query_string": {
        "query": "${txt}",
        "fields": ["title", "vid"]
      }
    }
    """

  def getSearches(txt:String) = s"""
    { "multi_match": { "query": "${txt}", "fields": [ "title", "vid" ] }}
    """

  def getSearch(txt:String) = s"""
    { "match": { "title": "${txt}" }}
    """

  def getTyping(txt:String) = 
    // s"""
    // { "multi_match": { "query": "${txt}", "type": "bool_prefix", "fields": [ "title", "vid" ] }}
    // """
    s"""
    { "multi_match": { "query": "${txt}", "type": "bool_prefix", "fields": [ "title", "title._3gram" ] }}
    """
}