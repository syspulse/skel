package io.syspulse.skel.video

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.video._

object VideoJson extends  DefaultJsonProtocol {
  implicit val jf_VID = jsonFormat1(VID.apply _)
  implicit val jf_vs = jsonFormat3(VideoSource.apply _)
  
  implicit val fmt = jsonFormat5(Video.apply _)
}
