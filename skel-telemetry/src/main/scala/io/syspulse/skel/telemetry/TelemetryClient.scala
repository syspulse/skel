package io.syspulse.skel.telemetry

import java.time.{Instant}
import java.nio.file.StandardOpenOption._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import akka.http.scaladsl._
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaRanges,MediaTypes, HttpMethods }
import java.util.concurrent.TimeUnit

import akka.util.ByteString

import java.nio.file.{Path,Paths}

import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global


import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.telemetry.Telemetry
import io.syspulse.skel.util.Util


trait TelemetryClient {
  val log = Logger(s"${this}")
  implicit val system = ActorSystem("ActorSystem-TelemetryClient")

  val logSink = Sink.foreach[Telemetry](t => println(s"${t.toLog}"))
  
  def getDataFileSink(logFile:String) = {
    if(logFile.trim.isEmpty) 
      Sink.ignore 
    else
      toSimpleLog.map(ByteString(_)).to(FileIO.toPath(Paths.get(Util.toFileWithTime(logFile)),options =  Set(WRITE, CREATE)))
  }

  def httpFlow(req: HttpRequest) = Http().singleRequest(req).flatMap(res => res.entity.dataBytes.runReduce(_ ++ _))

  def toJson(body:ByteString):String = body.utf8String.replaceAll("\\n","").replaceAll("\\s+","")

  val toLog = Flow[Telemetry].map(t=>s"${t.toLog}\n")
  val toSimpleLog = Flow[Telemetry].map(t=>s"${t.toSimpleLog}\n")

}