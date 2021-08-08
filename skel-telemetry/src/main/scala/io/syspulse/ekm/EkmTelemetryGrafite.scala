package io.syspulse.ekm

import akka.Done
import akka.actor._

import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaRanges,MediaTypes, HttpMethods }

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.scaladsl.{ Sink, Source, Flow, FileIO, Tcp}
import akka.util.ByteString

import scala.concurrent.duration._
import java.nio.file.Paths

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util


class EkmTelemetryGrafite extends EkmTelemetryClient {

  def toGrafite = Flow[EkmTelemetry].map(t => 
    { val ts = t.ts / 1000;
      s"ekm.telemetry.kwh ${t.kwhTotal} ${ts}\nekm.telemetry.v1 ${t.v1} ${ts}\nekm.telemetry.v2 ${t.v2} ${ts}\nekm.telemetry.v3 ${t.v3} ${ts}\n"
    })

  def getGrafiteFlow(grafiteUri:String) = {
    
    val (grafiteHost,grafitePort) = Util.getHostPort(grafiteUri)

    val grafiteConnection = Tcp().outgoingConnection(grafiteHost, grafitePort)
    toGrafite.log("Graphite").map(ByteString(_)).via(grafiteConnection)
  }

  def run(ekmHost:String, ekmKey:String, ekmDevice:String, interval:Long = 1, limit:Long = 0, logFile:String = "",
          grafiteUri:String = "localhost:2003") = {
        
    val ekmSource = getEkmSource(ekmHost,ekmKey,ekmDevice,interval,limit)
    val ekmSourceRestartable = RestartSource.withBackoff(retrySettings) { () =>
      log.info(s"Connecting -> EKM(${ekmHost})...")
      ekmSource.mapAsync(1)(getTelemetry(_)).log("EKM").map(toJson(_)).mapConcat(toData(_))
    }

    val grafiteFlow = RestartFlow.withBackoff(retrySettings) { () =>
      log.info(s"Connecting -> Graphite(${grafiteUri})...")
      getGrafiteFlow(grafiteUri)
    }
 
    val grafiteStream = ekmSourceRestartable.alsoTo(logSink).via(grafiteFlow).runWith(Sink.ignore)
 
    println(s"stream: ${grafiteStream}")
    //println(result.value.asInstanceOf[scala.util.Failure[_]].exception.getStackTrace.mkString("\n"))
    val r = Await.result(grafiteStream, Duration.Inf)
  }

}