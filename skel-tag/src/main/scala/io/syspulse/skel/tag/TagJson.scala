package io.syspulse.skel.tag

import scala.jdk.CollectionConverters._

import scala.util.Random

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.tag._

object TagJson extends  DefaultJsonProtocol {
  
  implicit val fmt = jsonFormat3(Tag.apply _)
}
