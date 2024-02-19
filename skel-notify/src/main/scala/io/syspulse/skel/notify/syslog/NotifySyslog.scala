package io.syspulse.skel.notify.syslog

import io.jvm.uuid._
import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import akka.actor.ActorSystem
import akka.util.ByteString
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.ExecutionContext

import io.syspulse.skel.syslog.{SyslogEvent,SyslogEventJson}

import io.syspulse.skel.uri.KafkaURI
import io.syspulse.skel.ingest.kafka.KafkaSink
import io.syspulse.skel.notify._

import io.syspulse.skel.util.Util

case class NotifySyslog(channel:Option[String])(implicit config: Config) extends NotifyReceiver[String] with KafkaSink[String] {  
  import spray.json._
  import SyslogEventJson._

  val kafkaUri = KafkaURI(s"${config.syslogUri}/${channel.getOrElse(config.syslogChannel)}")

  override def transform(t:String):ByteString = {
    ByteString(t.toString)
  }

  val kafka = Source.queue(10).toMat(sink(kafkaUri.broker,Set(kafkaUri.topic)))(Keep.left).run()

  // def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[String] = {
  //   log.info(s"severity=${severity}:scope=${scope.getOrElse(scope.getOrElse(""))}: title=${title},msg=${msg} -> Kafka(${kafkaUri})")
    
  //   //val m = s"""["title":"${title}","msg":"${msg}","ts":${System.currentTimeMillis()},"severity":${severity.getOrElse(-1)},"scope":"${scope.getOrElse("sys.all")}"]"""
  //   val m = SyslogEvent( subj = title, msg, severity, scope, from = None ).toJson.compactPrint
  //   val r = kafka.offer(m)
        
  //   Success(s"${r}")
  // }

  // def send(no:Notify):Try[String] = {
  //   send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  // }

  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[String] = {
    send(Notify(subj = Some(title),msg = msg,severity = severity,scope = scope))    
  }

  def send(no:Notify):Try[String] = {
    log.info(s"[${no}]-> Syslog(${kafkaUri})")

    val m = SyslogEvent( 
      subj = no.subj.getOrElse(""), 
      msg = no.msg, 
      severity = no.severity, 
      scope = no.scope, 
      from = no.from.map(_.toString), 
      id = Some(no.id)
    ).toJson.compactPrint
    // val r = kafka.offer(
    //   s"""["title":"${title}","msg":"${msg}","ts":${System.currentTimeMillis()},"severity":${severity.getOrElse(0)},"scope":"${scope.getOrElse("sys.all")}"]"""
    // )
    val r = kafka.offer(m)
    Success(s"${r}")
  }
}

