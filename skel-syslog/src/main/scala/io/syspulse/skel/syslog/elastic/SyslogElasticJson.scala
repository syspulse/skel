package io.syspulse.skel.syslog.elastic

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.syslog.Syslog

object SyslogElasticJson extends  DefaultJsonProtocol {
  implicit val fmt: JsonFormat[Syslog] = jsonFormat4(Syslog.apply _)
}

object SyslogElastic {
  import SyslogElasticJson._  
  
  def toElastic(o:Syslog) = o.toJson
  def fromElastic(json:String) = json.parseJson.convertTo[Syslog]
}