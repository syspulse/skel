package io.syspulse.skel.notify

import io.jvm.uuid._

import scala.concurrent.Future
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import akka.actor.typed.ActorSystem

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.notify.Notify
import io.syspulse.skel.notify.client.NotifyClientHttp

trait NotifyService {
  def findByEmail(email:String):Future[Option[Notify]]
  def create(email:String,name:String,xid:String):Future[Option[Notify]]
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
  
  def findByEmail(email:String)(implicit timeout:Timeout = timeout):Option[Notify] = {
    Await.result(service.findByEmail(email),timeout.duration)
  }

  def create(email:String,name:String,xid:String)(implicit timeout:Timeout = timeout):Option[Notify] = {
    Await.result(service.create(email,name,xid),timeout.duration)
  }
}


// --- For tests 
class NotifyServiceSim extends NotifyService {
  def findByEmail(email:String):Future[Option[Notify]] = Future.successful(None)

  def create(email:String,name:String,xid:String):Future[Option[Notify]] = {
    Future.successful(Some(Notify(UUID.random,email)))
  }
}
