package io.syspulse.skel.tag.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import akka.util.ByteString
import akka.http.javadsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest._
import io.syspulse.skel.ingest.flow.Pipeline

import spray.json._
import DefaultJsonProtocol._
import java.util.concurrent.TimeUnit

import io.syspulse.skel.tag._

import TagJson._
import TagCsv._

import io.syspulse.skel.tag.TagCsv

class PipelineTag(feed:String,output:String)(implicit config:Config)
  extends Pipeline[Tag,Tag,Tag](feed,output,config.throttle,config.delimiter,config.buffer) {

  def parse(data:String):Seq[Tag] = {
    fmtTag.get.parse(data)
  }
  
  def process:Flow[Tag,Tag,_] = Flow[Tag].map(v => v)

  def transform(v: Tag): Seq[Tag] = Seq(v)
}
