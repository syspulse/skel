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
import io.syspulse.skel.notify.NotifySeverity
import io.syspulse.skel.notify.Notify
import spray.json._
import io.syspulse.skel.syslog.{SyslogEvent,SyslogEventJson}

case class NotifyKafka(uri:String)(implicit config: Config) extends NotifyReceiver[String] with KafkaSink[String] {  
  import spray.json._
  import SyslogEventJson._
  
  val kafkaUri = KafkaURI(uri)

  override def transform(t:String):ByteString = {
    ByteString(t.toString)
  }

  val kafka = Source.queue(10).toMat(sink(kafkaUri.broker,Set(kafkaUri.topic)))(Keep.left).run()

  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[String] = {    
    send(Notify(subj = Some(title),msg = msg,severity = severity,scope = scope))    
  }

  def send(no:Notify):Try[String] = {
    log.info(s"[${no}]-> Kafka(${kafkaUri})")

    val m = SyslogEvent( 
      subj = no.subj.getOrElse(""), 
      msg = no.msg, 
      severity = no.severity, 
      scope = no.scope, 
      from = no.from.map(_.toString), 
      id = Some(no.id),
    ).toJson.compactPrint
    // val r = kafka.offer(
    //   s"""["title":"${title}","msg":"${msg}","ts":${System.currentTimeMillis()},"severity":${severity.getOrElse(0)},"scope":"${scope.getOrElse("sys.all")}"]"""
    // )
    val r = kafka.offer(m)        
    Success(s"${r}")
  }
}
