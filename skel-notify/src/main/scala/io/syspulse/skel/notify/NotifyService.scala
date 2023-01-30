package io.syspulse.skel.notify

import io.jvm.uuid._

import scala.concurrent.Future
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import akka.actor.typed.ActorSystem

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.Duration

import io.syspulse.skel.notify.Notify
import io.syspulse.skel.notify.client.NotifyClientHttp
import io.syspulse.skel.AwaitableService
import io.syspulse.skel.ExternalService

trait NotifyService extends ExternalService[NotifyService] {
  def notify(receivers:String,subj:String,msg:String):Future[Option[Notify]]
}

object NotifyService {
  var service:NotifyService = new NotifyServiceSim()
  val timeout:Timeout = Timeout(3000,TimeUnit.MILLISECONDS)

  def discover(uri:String = "")(implicit as:ActorSystem[_]):NotifyService = {
    service = uri match {
      case "test://" | "" => new NotifyServiceSim()
      case _ => new NotifyClientHttp(uri)(as,as.executionContext)
    }
    service
  }
  
  def notify(receivers:String,subj:String,msg:String)(implicit timeout:Timeout = timeout):Future[Option[Notify]] = {
    service.notify(receivers,subj,msg)
  }
}


// --- For tests 
class NotifyServiceSim extends NotifyService {
  val log = Logger(s"${this}")
  override def notify(to:String,subj:String,msg:String):Future[Option[Notify]] = {
    log.info(s"notify: ${to},${subj},${msg}")
    Future.successful(Some(Notify(Some(to),Some(subj),msg)))
  }

  def withAccessToken(token:String):NotifyServiceSim = this
  def withTimeout(timeout:Duration = Duration(1000, TimeUnit.MILLISECONDS)):NotifyServiceSim = this
}
