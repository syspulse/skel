package io.syspulse.skel.syslog.elastic

import scala.jdk.CollectionConverters._
import io.syspulse.skel.service.JsonCommon

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.syslog.Syslog

object SyslogElasticJson extends JsonCommon {
  implicit val fmt: JsonFormat[Syslog] = jsonFormat8(Syslog.apply _)
}

object SyslogElastic {
  import SyslogElasticJson._  
  
  def toElastic(o:Syslog) = o.toJson
  def fromElastic(json:String) = json.parseJson.convertTo[Syslog]
}