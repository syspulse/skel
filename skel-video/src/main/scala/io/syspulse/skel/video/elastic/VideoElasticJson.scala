package io.syspulse.skel.video.elastic

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.video._

object VideoElasticJson {
  import VideoJson._  
  
  def toElastic(o:Video) = o.toJson
  def fromElastic(json:String) = json.parseJson.convertTo[Video]
}