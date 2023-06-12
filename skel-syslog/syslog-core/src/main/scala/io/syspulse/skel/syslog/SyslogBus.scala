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

class FromKafka(uri:String,recv:(ByteString)=>ByteString) extends skel.ingest.kafka.KafkaSource[ByteString] {
  val kafkaUri = KafkaURI(uri)
    
  val source:Source[ByteString,_] = 
    source(kafkaUri.broker,Set(kafkaUri.topic),kafkaUri.group,offset = kafkaUri.offset)
    //.map(data => data.utf8String.parseJson.convertTo[T])
    //.filter(filter)
    .map(msg => recv(msg))

  val kafka = source.runWith(Sink.ignore)
}

class ToKafka(uri:String) extends skel.ingest.kafka.KafkaSink[ByteString] {
  val kafkaUri = KafkaURI(uri)
    
  val source = Source.queue(10)
    .toMat(sink(kafkaUri.broker,Set(kafkaUri.topic)))(Keep.left)//.run()
    
  // override def transform(t:T):ByteString = {
  //   ByteString(t.toJson.compactPrint)
  // }
  override def transform(t:ByteString):ByteString = t

  val kafka = source.run() //Flow[T].toMat(sink())(Keep.right) 

  def send(msg:ByteString) = kafka.offer(msg)
}


trait SyslogBusEngine {  
  def recv(msg:ByteString):ByteString
  def send(msg:ByteString):Try[SyslogBusEngine]
}


class SyslogBusKafka(busId:String,uri:String,channel:String = "sys.notify",
  receive:(ByteString)=>ByteString) extends SyslogBusEngine {
  
  private val log = Logger(s"${this}")
  
  val toKafka = new ToKafka(s"${uri}/${channel}/${busId}")
  val fromKafka = new FromKafka(s"${uri}/${channel}/${busId}",recv)

  def recv(msg:ByteString):ByteString = receive(msg)

  def send(msg:ByteString):Try[SyslogBusKafka] = {
    log.info(s"${msg} -> ${toKafka}")
    toKafka.send(msg)
    Success(this)
  }
}

class SyslogBusStd(
  receive:(ByteString)=>ByteString) extends SyslogBusEngine {
  
  private val log = Logger(s"${this}")
  import SyslogEventJson._
    
  def recv(msg:ByteString):ByteString = receive(msg)

  def send(msg:ByteString):Try[SyslogBusStd] = {
    log.info(s"${msg} -> ")
    Console.out.println(msg.utf8String)
    Success(this)
  }

  Util.stdin((txt:String) => {
    val msg = if(txt.isBlank()) SyslogEvent("stdin://","nothing").toJson.compactPrint else SyslogEvent("stdin://",txt).toJson.compactPrint
    recv(ByteString(msg))
    true
  })
}

abstract class SyslogBus(busId:String,uri:String = "kafka://localhost:9092",channel:String = "sys.notify") {  
  private val log = Logger(s"${this}")
  import SyslogEventJson._

  var scope:Option[String] = None

  val bus = uri.split("://").toList match {
    case "kafka" :: uri :: Nil => 
      new SyslogBusKafka(busId,uri,channel,receive)
    case ("std" | "stdin" | "stdout") :: Nil =>
      new SyslogBusStd(receive)
    case _ =>
      new SyslogBusStd(receive)
  }
  
  def filter(event:SyslogEvent):Boolean = 
    if(scope.isDefined) scope.get == event.scope.get else true 

  private def receive(data:ByteString):ByteString = {    
    val msg = data.utf8String.parseJson.convertTo[SyslogEvent]
    log.info(s"msg=${msg}")
    if(filter(msg))
      recv(msg)

    data
  }

  def recv(ev:SyslogEvent):SyslogEvent

  def send(ev:SyslogEvent):Try[SyslogBus] = {
    val msg = ByteString(ev.toJson.compactPrint)
    bus.send(msg).map(_ => this)    
  }

  def withScope(scope:String):SyslogBus = {
    this.scope = if(scope.isEmpty()) None else Some(scope)
    this
  }
}


