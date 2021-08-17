package io.syspulse.skel.ingest

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
import scala.util.Random


class IngestStdout extends IngestClient {

  type SourceData = Double
  type SourceDevice = String

  def toStdout = Flow[IngestData].map(t => 
    { s"Data(${t})" 
  })


  def getSourceRandom(freq:FiniteDuration = 1.seconds,limit:Long=0L,uniqNum:Int=3) = 
    Source.tick(1.seconds,freq,()=>Random.nextDouble())
      .take(if(limit==0L) Long.MaxValue else limit)
      .map(_())
      .map( r => {
        val device = Random.nextInt(uniqNum)
        (s"dev-${device}",r)
      })
  

  def run(sourceHost:String, sourceKey:String, sourceDevice:String, sourceFreq:Long = 1000L, limit:Long = 0, logFile:String = "") = {
        
    val sourceSource = getSourceRandom(Duration(sourceFreq,TimeUnit.MILLISECONDS),limit)

    val sourceRestartable = RestartSource.withBackoff(retrySettings) { () =>
      //log.info(s"Connecting -> Source(${sourceHost})...")
      //sourceSource.mapAsync(1)(getTelemetry(_)).log("Source").map(toJson(_)).mapConcat(toData(_))
      sourceSource
    }

    val sinkRestartable = RestartSink.withBackoff(retrySettings) { () =>
      //log.info(s"Connecting -> Graphite(${grafiteUri})...")
      Sink.foreach(println)
    }

    val logSink = Sink.ignore

    val count = Flow[(SourceDevice,SourceData)].map(d => {metricEventsCounter.inc() ;d})
    val transform = Flow[(SourceDevice,SourceData)].map( d => IngestData(d._1,System.currentTimeMillis,d._2.toString))
    val csv = Flow[IngestData].map(d => d.toCSV)

    val flow = sourceRestartable
      .via(count)
      .via(transform)
      .via(csv)
      .alsoTo(logSink)
      .runWith(sinkRestartable)
 
    println(s"flow: ${flow}")
    //println(result.value.asInstanceOf[scala.util.Failure[_]].exception.getStackTrace.mkString("\n"))
    //val r = Await.result(flow, Duration.Inf)
  }

}