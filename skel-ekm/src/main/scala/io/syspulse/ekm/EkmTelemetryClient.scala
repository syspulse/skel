package io.syspulse.ekm

import java.time.{Instant}

import akka.Done
import akka.actor._

import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaRanges,MediaTypes, HttpMethods }

import akka.stream._
import akka.stream.scaladsl.{ Sink, Source, Flow, FileIO, Tcp, RestartSource}
import akka.util.ByteString

import scala.concurrent.duration._
import java.nio.file.Paths

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import scala.jdk.CollectionConverters._

import io.syspulse.skel.ingest.IngestClient
import io.syspulse.skel.util.Util._

class EkmTelemetryClient extends IngestClient {

  val flowRandom = Flow[EkmTelemetry].map( t => EkmTelemetry(t.device,Instant.now.toEpochMilli,rnd(5000.0),rnd(240),rnd(240),rnd(240),rnd(500),rnd(500),rnd(500)))

  def ekmUri(host:String = "http://io.ekmpush.com", key:String, device:String, seconds:Long=1) = s"${host}/readMeter/v3/key/${key}/count/${seconds}/format/json/meters/${device}/"
  def putTelemetry(req: HttpRequest) = httpFlow(req)
  def getTelemetry(req: HttpRequest) = httpFlow(req)

  def toData(json:String):List[EkmTelemetry] = {
    val dd = ujson.read(json).obj("readMeter").obj("ReadSet").arr
    dd.map(rs => { 
      val device = s"${rs("Meter").str}-${rs("MAC_Addr").str}"
			rs("ReadData").arr.map(       
      	o=> { EkmTelemetry(device,o("Time_Stamp_UTC_ms").num.toLong,o("kWh_Tot").str.toDouble,
         	o("RMS_Volts_Ln_1").str.toDouble,o("RMS_Volts_Ln_2").str.toDouble,o("RMS_Volts_Ln_3").str.toDouble,
         	o("RMS_Watts_Ln_1").str.toDouble,o("RMS_Watts_Ln_2").str.toDouble,o("RMS_Watts_Ln_3").str.toDouble)
				}
      )
		}).flatten.toList
  }

  def getEkmSource(ekmHost:String, ekmKey:String, ekmDevice:String, freq:Long = 60, limit:Long = 0, logFile:String = "") = {
        
    val ekmFreq = FiniteDuration(freq,"seconds")
    
    val ekmHttpRequest = HttpRequest(uri = ekmUri(ekmHost,ekmKey,ekmDevice)).withHeaders(Accept(MediaTypes.`application/json`))
    val ekmSource = Source.tick(0.seconds, ekmFreq, ekmHttpRequest)
    
    if(limit == 0)
      ekmSource
    else  
      ekmSource.take(limit)
  }

}