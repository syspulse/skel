package io.syspulse.skel.telemetry

import java.time.{Instant}

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
import io.syspulse.skel.util.Util._


trait TelemetryClient {
  val log = Logger(s"${this}")

  val sinkLog = Sink.foreach[Telemetry](t => println(s"${t.toLog}"))
  def sinkLogFile(logFile:Path) = FileIO.toPath(logFile)

  def httpFlow(req: HttpRequest) = Http().singleRequest(req).flatMap(res => res.entity.dataBytes.runReduce(_ ++ _))

  def toJson(body:ByteString):String = body.utf8String.replaceAll("\\n","").replaceAll("\\s+","")

  val processRandom = Flow[Telemetry].map( t => Telemetry(t.device,Instant.now.toEpochMilli,rnd(5000.0),rnd(240),rnd(240),rnd(240),rnd(500),rnd(500),rnd(500)))

  val toLog = Flow[Telemetry].map(t=>s"${t.toLog}\n")
  val toSimpleLog = Flow[Telemetry].map(t=>s"${t.toSimpleLog}\n")

  implicit val system = ActorSystem("ActorSystem-TelemetryClient")

}