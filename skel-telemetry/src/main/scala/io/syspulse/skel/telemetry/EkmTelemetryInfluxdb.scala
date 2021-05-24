package io.syspulse.skel.telemetry

import akka.Done
import akka.NotUsed
import akka.actor._

import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaRanges,MediaTypes, HttpMethods }

import akka.stream._
import akka.stream.scaladsl.{ Sink, Source, Flow, FileIO, Tcp}
import akka.stream.scaladsl._
import akka.stream.scaladsl.RunnableGraph

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

class EkmTelemetryInfluxdb extends EkmTelemetryClient {

  def toInflux(tt:Seq[EkmTelemetry]) = tt.map( t=>(InfluxDbWriteMessage(Point.measurement(t.device).time(t.ts, TimeUnit.MILLISECONDS).
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

    Flow[EkmTelemetry].map(t => toInflux(Seq(t))).via(InfluxDbFlow.create())
  }

  def getInfluxSink(influxUri: String, influxUser:String, influxPass:String, influxDb:String) = {
    implicit val influxDB = InfluxDBFactory.connect(influxUri, influxUser, influxPass) 
    influxDB.setDatabase(influxDb)
    Flow[EkmTelemetry].map(t => toInflux(Seq(t))).to(InfluxDbSink.create())
  }

  
  def run(ekmHost:String,ekmKey:String, ekmDevice:String, interval:Long = 1, limit:Long = 0, logFile:String = "",
          influxUri: String = "http://localhost:8086", influxUser:String="ekm_user", influxPass:String="ekm_pass", influxDb:String="ekm_db" ) = {
        
    val ekmSource = getEkmSource(ekmHost:String,ekmKey,ekmDevice,interval,limit)

    val ekmFlow = ekmSource.mapAsync(1)(getTelemetry(_)).map(toJson(_)).mapConcat(toData(_))

    val influxFlow = getInfluxFlow(influxUri,influxUser,influxPass,influxDb)
    val influxSink = getInfluxSink(influxUri,influxUser,influxPass,influxDb)
    val dataFileSink = getDataFileSink(logFile)

    val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val broadcast = b.add(Broadcast[EkmTelemetry](3))
      ekmFlow ~> broadcast.in
      broadcast.out(0) ~> Flow[EkmTelemetry].async ~> dataFileSink
      broadcast.out(1) ~> Flow[EkmTelemetry].async ~> influxSink
      broadcast.out(2) ~> Flow[EkmTelemetry].async ~> logSink
      ClosedShape
    })

    println(s"stream: ${graph}")
    graph.run()
    //val influxStream = ekmSource.mapAsync(1)(getTelemetry(_)).map(toJson(_)).mapConcat(toData(_)).alsoTo(logSink).via(influxFlow).runWith(Sink.ignore)
    //println(influxStream)

    //println(result.value.asInstanceOf[scala.util.Failure[_]].exception.getStackTrace.mkString("\n"))
    //val r = Await.result(influxStream, Duration.Inf)
    //println(r)

  }

}