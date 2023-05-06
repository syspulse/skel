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
import akka.stream.alpakka.file.scaladsl.LogRotatorSink

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

class FromKafka[T](uri:String,recv:(T)=>T)(implicit fmt:JsonFormat[T]) extends skel.ingest.kafka.KafkaSource[T] {
  val kafkaUri = KafkaURI(uri)
    
  val source:Source[T,_] = 
    source(kafkaUri.broker,Set(kafkaUri.topic),kafkaUri.group,offset = kafkaUri.offset)
    .map(data => data.utf8String.parseJson.convertTo[T])
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

abstract class SyslogBus[T](uri:String = "kafka://localhost:9092",channel:String = "sys.notify")(implicit fmt:JsonFormat[T]) {
  private val log = Logger(s"${this}")
  
  val toKafka = new ToKafka(s"${uri}/${channel}")
  val fromKafka = new FromKafka(s"${uri}/${channel}",recv)

  def recv(msg:T):T

  def send(msg:T):Try[SyslogBus[T]] = {
    log.info(s"${msg} -> ${toKafka}")
    toKafka.send(msg)
    Success(this)
  }

}
