package io.syspulse.skel.telemetry

import akka.Done
import akka.actor._

import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaRanges,MediaTypes, HttpMethods }

import akka.stream._
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

import akka.stream.alpakka.influxdb.scaladsl.InfluxDbSink
import akka.stream.alpakka.influxdb.scaladsl.InfluxDbSource
import akka.stream.alpakka.influxdb.scaladsl.InfluxDbFlow
import akka.stream.alpakka.influxdb.InfluxDbWriteMessage
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Query
import org.influxdb.annotation.Measurement
import org.influxdb.dto.Point

class EkmTelemetryInfluxdb extends EkmTelemetry {

  def toInflux(tt:Seq[Telemetry]) = tt.map( t=>(InfluxDbWriteMessage(Point.measurement(t.device).time(t.ts, TimeUnit.MILLISECONDS).
    addField("kwh", t.kwhTotal).
    addField("v1", t.v1).
    addField("v2", t.v2).
    addField("v3", t.v3).
    addField("w1", t.w1).
    addField("w2", t.w2).
    addField("w3", t.w3).
    build() )))

  def getInfluxFlow(influxUri: String, influxUser:String, influxPass:String, influxDb:String) = {
    implicit val influxDB = InfluxDBFactory.connect(influxUri, influxUser, influxPass) 
    influxDB.setDatabase(influxDb)

    Flow[Telemetry].map(t=>toInflux(Seq(t))).via(InfluxDbFlow.create())
  }

  def run(ekmHost:String,ekmKey:String, ekmDevice:String, interval:Long = 1, limit:Long = 0, logFile:String = "",
          influxUri: String = "http://localhost:8086", influxUser:String="ekm_user", influxPass:String="ekm_pass", influxDb:String="ekm_db" ) = {
        
    val ekmSource = getEkmSource(ekmHost:String,ekmKey,ekmDevice,interval,limit)

    val influxFlow = getInfluxFlow(influxUri,influxUser,influxPass,influxDb)

    val streamInflux = ekmSource.mapAsync(1)(getTelemetry(_)).map(toJson(_)).mapConcat(toData(_)).alsoTo(sinkLog).via(influxFlow).runWith(Sink.ignore)

    println(streamInflux)
    //println(result.value.asInstanceOf[scala.util.Failure[_]].exception.getStackTrace.mkString("\n"))
    val r = Await.result(streamInflux, Duration.Inf)
    //println(r)

  }

}