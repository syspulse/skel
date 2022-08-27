package io.syspulse.skel.ingest.flow

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

import spray.json._
import DefaultJsonProtocol._
import java.util.concurrent.TimeUnit

abstract class Pipeline[I,T, O <: skel.Ingestable](feed:String,output:String,throttle:Long = 0)(implicit config:Config) extends IngestFlow[I,T,O]() {

  def processing:Flow[I,T,_]

  override def process:Flow[I,T,_] = {
    val f0 = processing
    val f1 = if(throttle != 0L) 
      f0.throttle(1,FiniteDuration(throttle,TimeUnit.MILLISECONDS))
    else
      f0    
    f1
  }
  
  override def source() = {
    val source = feed.split("://").toList match {
      case "kafka" :: _ => Flows.fromKafka[Textline](feed)
      case "http" :: _ => Flows.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = config.delimiter,frameSize = config.buffer.toInt)
      case "https" :: _ => Flows.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = config.delimiter,frameSize = config.buffer.toInt)
      case "file" :: fileName :: Nil => Flows.fromFile(fileName,1024,frameDelimiter = config.delimiter, frameSize = config.buffer.toInt)
      case "stdin" :: _ => Flows.fromStdin()
      case _ => Flows.fromFile(feed,1024,frameDelimiter = config.delimiter,frameSize = config.buffer.toInt)
    }
    source
  }

  implicit val fmt:JsonFormat[O]
  
  override def sink() = {
    val sink = output.split("://").toList match {
      case "kafka" :: _ => Flows.toKafka[O](output)
      case "elastic" :: _ => Flows.toElastic[O](output)
      case "file" :: fileName :: Nil => Flows.toFile(fileName)
      case "hive" :: fileName :: Nil => Flows.toHiveFile(fileName)
      case "stdout" :: _ => Flows.toStdout()
      case "" :: Nil => Flows.toStdout()
      case _ => Flows.toFile(output)
    }
    sink
  }

}