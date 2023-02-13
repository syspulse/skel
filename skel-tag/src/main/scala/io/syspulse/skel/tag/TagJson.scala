package io.syspulse.skel.tag

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.tag._

object TagJson extends  DefaultJsonProtocol {
  
  implicit val jf_tag = jsonFormat5(Tag.apply _)
  implicit val jf_tags = jsonFormat2(Tags.apply _)
}
