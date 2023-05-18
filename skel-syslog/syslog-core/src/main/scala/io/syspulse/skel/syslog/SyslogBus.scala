package io.syspulse.skel.syslog

import scala.util.{Try,Success,Failure}

import scala.jdk.CollectionConverters._

import java.nio.file.StandardOpenOption._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import java.nio.file.{Path,Paths, Files}

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import spray.json.JsonFormat
import spray.json._

import io.syspulse.skel.Ingestable
import io.syspulse.skel
import io.syspulse.skel.util.Util

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.Http
import java.util.concurrent.TimeUnit

import io.syspulse.skel.uri.KafkaURI

trait SyslogFlow[T] {
  private val log = Logger(s"${this}")
  implicit val system = ActorSystem("ActorSystem-SyslogFlow")
  
  val retrySettings = RestartSettings(
    minBackoff = FiniteDuration(3000,TimeUnit.MILLISECONDS),
    maxBackoff = FiniteDuration(10000,TimeUnit.MILLISECONDS),
    randomFactor = 0.2
  )
  //.withMaxRestarts(10, 5.minutes)

  def debug = Flow.fromFunction( (data:ByteString) => { log.info(s"data=${data}"); data})

}

class FromKafka[T](uri:String,recv:(T)=>T,filter:(T)=>Boolean)(implicit fmt:JsonFormat[T]) extends skel.ingest.kafka.KafkaSource[T] {
  val kafkaUri = KafkaURI(uri)
    
  val source:Source[T,_] = 
    source(kafkaUri.broker,Set(kafkaUri.topic),kafkaUri.group,offset = kafkaUri.offset)
    .map(data => data.utf8String.parseJson.convertTo[T])
    .filter(filter)
    .map(msg => recv(msg))

  val kafka = source.runWith(Sink.ignore)
}

class ToKafka[T](uri:String)(implicit fmt:JsonFormat[T]) extends skel.ingest.kafka.KafkaSink[T] {
  val kafkaUri = KafkaURI(uri)
    
  val source = Source.queue(10)
    .toMat(sink(kafkaUri.broker,Set(kafkaUri.topic)))(Keep.left)//.run()
  
  //def sink():Sink[T,_] = sink(kafkaUri.broker,Set(kafkaUri.topic))
  
  override def transform(t:T):ByteString = {
    ByteString(t.toJson.compactPrint)
  } 

  val kafka = source.run() //Flow[T].toMat(sink())(Keep.right) 

  def send(msg:T) = kafka.offer(msg)
}

abstract class SyslogBusKafka[T](busId:String,uri:String = "kafka://localhost:9092",channel:String = "sys.notify")(implicit fmt:JsonFormat[T]) {
  private val log = Logger(s"${this}")
  
  val toKafka = new ToKafka(s"${uri}/${channel}/${busId}")
  val fromKafka = new FromKafka(s"${uri}/${channel}/${busId}",recv,filter)

  def filter(msg:T):Boolean

  def recv(msg:T):T

  def send(msg:T):Try[SyslogBusKafka[T]] = {
    log.info(s"${msg} -> ${toKafka}")
    toKafka.send(msg)
    Success(this)
  }
}

abstract class SyslogBus(busId:String,uri:String = "kafka://localhost:9092",channel:String = "sys.notify") extends SyslogBusKafka(busId,uri,channel)(SyslogEventJson.jf_Notify) {  
  private val log = Logger(s"${this}")
  var scope:Option[String] = None
  
  def filter(event:SyslogEvent):Boolean = 
    if(scope.isDefined) scope.get == event.scope.get else true  

  def recv(msg:SyslogEvent):SyslogEvent

  def withScope(scope:String):SyslogBus = {
    this.scope = if(scope.isEmpty()) None else Some(scope)
    this
  }
}
