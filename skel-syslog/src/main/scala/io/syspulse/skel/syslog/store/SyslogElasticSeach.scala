package io.syspulse.skel.syslog.store

import scala.jdk.CollectionConverters._
import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic.ElasticSearch
import io.syspulse.skel.syslog.Syslog

trait SyslogElasticSearch extends ElasticSearch[Syslog] {

  import io.syspulse.skel.syslog.elastic.SyslogElasticJson
  import io.syspulse.skel.syslog.elastic.SyslogElasticJson._
  
  implicit val fmt = SyslogElasticJson.fmt 

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