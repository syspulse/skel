package io.syspulse.skel.ingest

import java.time.{Instant}
import java.nio.file.StandardOpenOption._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}

import scala.concurrent.duration._
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

import io.prometheus.client.Counter

import io.syspulse.skel
import io.syspulse.skel.ingest.Ingestable
import io.syspulse.skel.util.Util


trait IngestClient {
  val log = Logger(s"${this}")

  val metricEventsCounter: Counter = Counter.build().name("skel_ingest_events_total").help("Total Ingested Events").register()

  implicit val system = ActorSystem("ActorSystem-IngestClient")

  val retrySettings = RestartSettings(
    minBackoff = 3.seconds,
    maxBackoff = 10.seconds,
    randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
  ).withMaxRestarts(10, 5.minutes)

  val logSink = Sink.foreach[Ingestable](t => println(s"${t.toLog}"))
  
  def getDataFileSink(logFile:String) = {
    if(logFile.trim.isEmpty) 
      Sink.ignore 
    else
      toSimpleLog.map(ByteString(_)).to(FileIO.toPath(Paths.get(Util.toFileWithTime(logFile)),options =  Set(WRITE, CREATE)))
  }

  def countFlow(d:ByteString) = {metricEventsCounter.inc(); d}

  def httpFlow(req: HttpRequest) = Http().singleRequest(req).flatMap(res => res.entity.dataBytes.runReduce(_ ++ _))

  def toJson(body:ByteString):String = body.utf8String.replaceAll("\\n","").replaceAll("\\s+","")

  val toLog = Flow[Ingestable].map(t=>s"${t.toLog}\n")
  val toSimpleLog = Flow[Ingestable].map(t=>s"${t.toSimpleLog}\n")

}