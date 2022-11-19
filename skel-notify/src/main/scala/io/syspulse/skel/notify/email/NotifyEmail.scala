package io.syspulse.skel.notify.email

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.util.concurrent.TimeUnit
import scala.concurrent.{Awaitable,Await,Future}
import scala.concurrent.duration.{Duration,FiniteDuration}

import io.jvm.uuid._

import courier._, Defaults._
import scala.util._

import io.syspulse.skel.notify.Config
import io.syspulse.skel.notify.NotifyReceiver
import javax.mail.internet.InternetAddress
import java.util.concurrent.TimeoutException

class SMTP(host:String,port:Int,smtpUser:String,smtpPass:String,tls:Boolean,starttls:Boolean) {
  val m0 = Mailer(host,port)
               .auth(true)
               .as(smtpUser,smtpPass)
               .debug(true)
  val m1 = if(tls) m0.ssl(tls) else m0
  val m2 = if(starttls) m1.startTls(starttls) else m1
               
  val mailer = m2()   
  override def toString() = s"SMTP(${host}:${port}/$smtpUser/****,tls=${tls},starttls=${starttls})"
}

object SMTP {
  val smtps:Map[String,SMTP] = Map()
  def get(name:String = "smtp")(implicit config: Config):SMTP = {
    val smtp = smtps.get(name)
    smtp match {
      case Some(smtp) => smtp
      case None => 
        val uri = SmtpURI(config.smtpUri)
        val smtp = new SMTP(uri.host,uri.port,uri.user,uri.pass,uri.tls,uri.starttls)
        smtp
    }
  }
}

class NotifyEmail(smtpName:String,to:String)(implicit config: Config) extends NotifyReceiver[String] {
  val log = Logger(s"${this}")

  val from = config.smtpFrom
  val timeout = config.timeout

  def send(title:String,msg:String):Try[String] = {
    val smtp = SMTP.get(smtpName)(config)

    log.info(s"[${to}]-> ${smtp}")
    
    val mailer  = smtp.mailer
    
    val f = mailer(Envelope.from(new InternetAddress(from))
        .to(new InternetAddress(to))
        .subject(title)
        .content(Text(msg)))
    
    // f.onComplete {
    //       case Success(_) => println("message delivered")
    //       case Failure(_) => println("delivery failed")
    //     }

    try {
      val r = Await.result(f,FiniteDuration(timeout,TimeUnit.MILLISECONDS))
      Success(s"${to}: sent")
    } catch {
      case e: TimeoutException => Failure(e)
    }    
  }
}
