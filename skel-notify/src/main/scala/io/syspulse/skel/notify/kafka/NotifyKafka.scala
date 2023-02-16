package io.syspulse.skel.notify.kafka

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.util.concurrent.TimeUnit
import scala.concurrent.{Awaitable,Await,Future}
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.TimeoutException
import java.net.URLEncoder

import akka.util.ByteString
import akka.stream._
import akka.stream.scaladsl._

import io.jvm.uuid._
import spray.json._

import io.syspulse.skel.notify.Config
import io.syspulse.skel.notify.NotifyReceiver
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import io.syspulse.skel.uri.KafkaURI
import io.syspulse.skel.ingest.kafka.KafkaSink


class NotifyKafka(uri:String)(implicit config: Config) extends NotifyReceiver[String] with KafkaSink[String] {  
  val kafkaUri = KafkaURI(uri)

  override def transform(t:String):ByteString = {
    ByteString(t.toString)
  }

  val kafka = Source.queue(10).toMat(sink(kafkaUri.broker,Set(kafkaUri.topic)))(Keep.left).run()

  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[String] = {    
    log.info(s"[${msg}]-> Kafka(${kafkaUri})")

    //val f = TelegramHttpClient.sendMessage(telUri,s"${title}: ${msg}")
    val r = kafka.offer(s"""["title":"${title}","msg":"${msg}","ts":${System.currentTimeMillis()}, "severity": ${severity.getOrElse(0)}, "scope": "${scope.getOrElse("sys.none")}"]""")
        
    Success(s"${r}")
  }
}
