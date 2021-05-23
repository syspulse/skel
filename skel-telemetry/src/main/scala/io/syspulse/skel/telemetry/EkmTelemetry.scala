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

class EkmTelemetry extends TelemetryClient {

  def ekmUri(host:String = "http://io.ekmpush.com", key:String, device:String, seconds:Long=1) = s"${host}/readMeter/v3/key/${key}/count/${seconds}/format/json/meters/${device}/"
  def putTelemetry(req: HttpRequest) = httpFlow(req)
  def getTelemetry(req: HttpRequest) = httpFlow(req)

  def toData(json:String) = {
    val dd = ujson.read(json).obj("readMeter").obj("ReadSet").arr
    dd.map(rs => { 
      val device = s"${rs("Meter").str}-${rs("MAC_Addr").str}"
			rs("ReadData").arr.map(       
      	o=> { Telemetry(device,o("Time_Stamp_UTC_ms").num.toLong,o("kWh_Tot").str.toDouble,
         	o("RMS_Volts_Ln_1").str.toDouble,o("RMS_Volts_Ln_2").str.toDouble,o("RMS_Volts_Ln_3").str.toDouble,
         	o("RMS_Watts_Ln_1").str.toDouble,o("RMS_Watts_Ln_2").str.toDouble,o("RMS_Watts_Ln_3").str.toDouble)
				}
      )
		}).flatten.toList
  }

  def getEkmSource(ekmHost:String, ekmKey:String, ekmDevice:String, interval:Long = 1, limit:Long = 0, logFile:String = "") = {
        
    val ekmFreq = 60.seconds
    //val logFile = Paths.get("app_data/ekm-0002.log")

    val ekmHttpRequest = HttpRequest(uri = ekmUri(ekmHost,ekmKey,ekmDevice)).withHeaders(Accept(MediaTypes.`application/json`))
    val ekmSource = Source.tick(0.seconds, ekmFreq, ekmHttpRequest)
    
    if(limit == 0)
      ekmSource
    else  
      ekmSource.take(limit)
  }

}