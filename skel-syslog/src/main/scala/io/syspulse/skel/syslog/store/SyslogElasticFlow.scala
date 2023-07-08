package io.syspulse.skel.syslog.store

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic.ElasticFlow
import akka.stream.scaladsl.Flow

import io.syspulse.skel.syslog.Syslog

class SyslogElasticFlow extends ElasticFlow[Syslog,Syslog] {
  
  import io.syspulse.skel.syslog.elastic.SyslogElasticJson
  import io.syspulse.skel.syslog.elastic.SyslogElasticJson._
  implicit val fmt = SyslogElasticJson.fmt 

  
  def process:Flow[Syslog,Syslog,_] = Flow[Syslog].map(v => v)

  override def parse(data:String):Seq[Syslog] = data.split("\n").toIndexedSeq.flatMap( line => 
    line.split(",",-1).toList match {
      case ts :: severity :: scope :: msg :: Nil => Some(Syslog(ts = ts.toLong,severity = Some(severity.toInt),scope = Some(scope),msg = msg))
      case _ => None
    }
  )
  
  override def getIndex(d:Syslog):(String,Syslog) = (s"${d.msg}",d)
  
}