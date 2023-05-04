package io.syspulse.skel.notify

import io.jvm.uuid._
import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import akka.actor.ActorSystem
import akka.util.ByteString
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.ExecutionContext

import io.syspulse.skel.uri.KafkaURI
import io.syspulse.skel.ingest.kafka.KafkaSink
import io.syspulse.skel.notify.NotifySeverity

import io.syspulse.skel.util.Util

// class NotifySyslog(scope:Option[String]) extends NotifyReceiver[Option[_]] {
//   val kafkaUri = KafkaURI(s"kafka://${scope.getOrElse("sys.all")}")
  
//   def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scopeOver:Option[String]):Try[Option[_]] = {
//     println(s"severity=${severity}:scope=${scopeOver.getOrElse(scope.getOrElse(""))}: title=${title},msg=${msg}")
//     Success(None)
//   }
// }


class NotifySyslog(channel:Option[String])(implicit config: Config) extends NotifyReceiver[String] with KafkaSink[String] {  
  import spray.json._
  import SyslogEventJson._

  val kafkaUri = KafkaURI(s"${config.kafkaUri}/${channel.getOrElse("sys.notify")}")

  override def transform(t:String):ByteString = {
    ByteString(t.toString)
  }

  val kafka = Source.queue(10).toMat(sink(kafkaUri.broker,Set(kafkaUri.topic)))(Keep.left).run()

  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[String] = {
    log.info(s"severity=${severity}:scope=${scope.getOrElse(scope.getOrElse(""))}: title=${title},msg=${msg} -> Kafka(${kafkaUri})")
    
    //val m = s"""["title":"${title}","msg":"${msg}","ts":${System.currentTimeMillis()},"severity":${severity.getOrElse(-1)},"scope":"${scope.getOrElse("sys.all")}"]"""
    val m = SyslogEvent( title, msg, System.currentTimeMillis, severity, scope).toJson.compactPrint
    val r = kafka.offer(m)
        
    Success(s"${r}")
  }
}

