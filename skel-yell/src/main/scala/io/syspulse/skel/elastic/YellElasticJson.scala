package io.syspulse.skel.yell.elastic

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.yell.Yell

object YellElasticJson extends  DefaultJsonProtocol {
  implicit val fmt: JsonFormat[Yell] = jsonFormat4(Yell.apply _)
}

object YellElastic {
  import YellElasticJson._  
  
  def toElastic(o:Yell) = o.toJson
  def fromElastic(json:String) = json.parseJson.convertTo[Yell]
}