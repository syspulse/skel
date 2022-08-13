package io.syspulse.skel.yell

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic._
import akka.stream.scaladsl.Sink
import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.NotUsed

class YellFlow extends ElasticFlow[Yell] {
  
  import io.syspulse.skel.yell.YellElasticJson._
  implicit val fmt = YellElasticJson.fmt 

  // override def sink():Sink[WriteMessage[Yell,NotUsed],Any] = 
  //   Sink.foreach(println _)

  override def parse(data:String):Seq[Yell] = data.split("\n").flatMap( line => 
    line.split(",").toList match {
      case ts :: lvl :: area :: txt :: Nil => Some(Yell(ts.toLong,lvl.toInt,area,txt))
      case _ => None
    }
  )
  

  override def getIndex(d:Yell):(String,Yell) = (s"${d.text}",d)
  
}