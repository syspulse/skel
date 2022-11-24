package io.syspulse.skel.telemetry.flow

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
import io.syspulse.skel.ingest.store._
import io.syspulse.skel.ingest.flow.Pipeline

import spray.json._
import DefaultJsonProtocol._
import java.util.concurrent.TimeUnit

import io.syspulse.skel.telemetry._
import io.syspulse.skel.telemetry.parser.TelemetryParser

//import TelemetryJson._

class PipelineTelemetry(feed:String,output:String)(implicit config:Config,parser:TelemetryParser,fmt:JsonFormat[Telemetry])
  extends Pipeline[Telemetry,Telemetry,Telemetry](feed,output,config.throttle,config.delimiter,config.buffer) {

  def parse(data:String):Seq[Telemetry] = parser.fromString(data).toSeq
  
  override def processing:Flow[Telemetry,Telemetry,_] = Flow[Telemetry].map(v => v)

  def transform(v: Telemetry): Seq[Telemetry] = Seq(v)
}
