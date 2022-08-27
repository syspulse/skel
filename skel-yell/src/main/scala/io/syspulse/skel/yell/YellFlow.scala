package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic.ElasticFlow
import akka.stream.scaladsl.Flow

class YellFlow extends ElasticFlow[Yell,Yell] {
  
  import io.syspulse.skel.yell.elastic.YellElasticJson
  import io.syspulse.skel.yell.elastic.YellElasticJson._
  implicit val fmt = YellElasticJson.fmt 

  
  def process:Flow[Yell,Yell,_] = Flow[Yell].map(v => v)

  override def parse(data:String):Seq[Yell] = data.split("\n").toIndexedSeq.flatMap( line => 
    line.split(",").toList match {
      case ts :: lvl :: area :: txt :: Nil => Some(Yell(ts.toLong,lvl.toInt,area,txt))
      case _ => None
    }
  )
  
  override def getIndex(d:Yell):(String,Yell) = (s"${d.text}",d)
  
}