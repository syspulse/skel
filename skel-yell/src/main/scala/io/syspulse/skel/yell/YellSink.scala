package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic._

class YellSink extends ElasticSink[Yell] {
  
  import io.syspulse.skel.yell.YellElasticJson._
  implicit val fmt = YellElasticJson.fmt 

  override def parse(data:String):Seq[Yell] = Seq(
    data.split(",").toList match {
      case ts :: lvl :: area :: txt :: Nil => Some(Yell(ts.toLong,lvl.toInt,area,txt))
      case _ => None
    }
  ).flatten
  

  override def getIndex(d:Yell):(String,Yell) = (s"${d.text}",d)
  
}