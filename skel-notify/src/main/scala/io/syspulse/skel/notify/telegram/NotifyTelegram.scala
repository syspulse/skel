package io.syspulse.skel.notify.telegram

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.util.concurrent.TimeUnit
import scala.concurrent.{Awaitable,Await,Future}
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.TimeoutException
import java.net.URLEncoder

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.util.ByteString

import io.jvm.uuid._
import spray.json._

import io.syspulse.skel.notify.Config
import io.syspulse.skel.notify.NotifyReceiver
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import io.syspulse.skel.notify.Notify

object TelegramHttpClient {
  val log = Logger(s"${this}")

  implicit val as:ActorSystem = ActorSystem("TelegramHttpClient").classicSystem
  implicit val ec:ExecutionContext = as.getDispatcher

  def reqSendMessage(telUri:TelegramURI,data:String) = HttpRequest(method = HttpMethods.GET, 
    uri = s"https://api.telegram.org/bot${telUri.key}/sendMessage" +
              s"?chat_id=${URLEncoder.encode(telUri.channel, "UTF-8")}" +
              s"&parse_mode=MarkdownV2" +
              s"&text=${URLEncoder.encode(data, "UTF-8")}"
  )

    
  def sendMessage(telUri:TelegramURI,data:String):Future[String] = {    
    val req = reqSendMessage(telUri,data)
    log.info(s"req=${req}")
    for {
      rsp <- Http().singleRequest(req)
      body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    } yield {
      val rsp = body.utf8String
      log.info(s"rsp='${rsp}'")
      rsp
    }
  }
}

class NotifyTelegram(uri:String)(implicit config: Config) extends NotifyReceiver[String] {
  val log = Logger(s"${this}")

  val telUri = TelegramURI(uri,Option(config.telegramUri))

  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[String] = {    
    log.info(s"[${msg}]-> Telegram(${telUri})")

    val f = TelegramHttpClient.sendMessage(telUri,s"${severity}:${scope}: ${title}: ${msg}")
        
    // f.onComplete {
    //       case Success(_) => println("message delivered")
    //       case Failure(_) => println("delivery failed")
    //     }

    try {
      val r = Await.result(f,FiniteDuration(3000,TimeUnit.MILLISECONDS))
      Success(s"${r}")
    } catch {
      case e: TimeoutException => Failure(e)
    }    
  }

  def send(no:Notify):Try[String] = {
    send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  }
}
