package io.syspulse.skel.syslog

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic.ElasticFlow
import akka.stream.scaladsl.Flow

class SyslogFlow extends ElasticFlow[Syslog,Syslog] {
  
  import io.syspulse.skel.syslog.elastic.SyslogElasticJson
  import io.syspulse.skel.syslog.elastic.SyslogElasticJson._
  implicit val fmt = SyslogElasticJson.fmt 

  
  def process:Flow[Syslog,Syslog,_] = Flow[Syslog].map(v => v)

  override def parse(data:String):Seq[Syslog] = data.split("\n").toIndexedSeq.flatMap( line => 
    line.split(",").toList match {
      case ts :: lvl :: area :: txt :: Nil => Some(Syslog(ts.toLong,lvl.toInt,area,txt))
      case _ => None
    }
  )
  
  override def getIndex(d:Syslog):(String,Syslog) = (s"${d.text}",d)
  
}