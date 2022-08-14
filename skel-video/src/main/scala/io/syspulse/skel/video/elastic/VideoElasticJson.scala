package io.syspulse.skel.video.elastic

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.video._

object VideoElasticJson extends  DefaultJsonProtocol {
  implicit val jf_VID: JsonFormat[VID] = jsonFormat1(VID.apply _)
  implicit val fmt: JsonFormat[Video] = jsonFormat3(Video.apply _)
}

object VideoElastic {
  import VideoElasticJson._  
  
  def toElastic(o:Video) = o.toJson
  def fromElastic(json:String) = json.parseJson.convertTo[Video]
}