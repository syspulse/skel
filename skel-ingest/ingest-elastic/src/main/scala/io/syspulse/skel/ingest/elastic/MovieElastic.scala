package io.syspulse.skel.ingest.elastic

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.video._

object MovieElasticJson extends  DefaultJsonProtocol {
  implicit val format: JsonFormat[Movie] = jsonFormat3(Movie)
}

object MovieElastic {
  import MovieElasticJson._  
  
  def toElastic(o:Movie) = o.toJson
  def fromElastic(json:String) = json.parseJson.convertTo[Movie]
}