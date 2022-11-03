package io.syspulse.skel.user

import io.jvm.uuid._

import scala.concurrent.Future
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import akka.actor.typed.ActorSystem

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.user.User
import io.syspulse.skel.user.client.UserClientHttp
import io.syspulse.skel.AwaitableService

trait UserService extends AwaitableService[UserService] {
  def findByEmail(email:String):Future[Option[User]]
  def create(email:String,name:String,xid:String,avatar:String):Future[Option[User]]
  def delete(id:UUID):Future[UserActionRes]
  def get(id:UUID):Future[Option[User]]
  def getByXid(xid:String):Future[Option[User]]    
  def getByXidAlways(xid:String):Future[Option[User]]
  def all():Future[Users]
}

object UserService {
  var service:UserService = new UserServiceSim()
  val timeout:Timeout = Timeout(3000,TimeUnit.MILLISECONDS)

  def discover(uri:String = "")(implicit as:ActorSystem[_]):UserService = {
    service = uri match {
      case "test://" | "" => new UserServiceSim()
      case _ => new UserClientHttp(uri)(as,as.executionContext)
    }
    service
  }
  
  def findByEmail(email:String)(implicit timeout:Timeout = timeout):Option[User] = {
    Await.result(service.findByEmail(email),timeout.duration)
  }

  def create(email:String,name:String,xid:String,avatar:String="")(implicit timeout:Timeout = timeout):Option[User] = {
    Await.result(service.create(email,name,xid,avatar),timeout.duration)    
  }
}


// --- For tests 
class UserServiceSim extends UserService {
  def findByEmail(email:String):Future[Option[User]] = Future.successful(None)

  def create(email:String,name:String,xid:String,avatar:String):Future[Option[User]] = {
    Future.successful(Some(User(UUID.random,email,name,xid,avatar)))
  }

  def delete(id:UUID):Future[UserActionRes] = Future.successful(UserActionRes("",None))
  def get(id:UUID):Future[Option[User]] = Future.successful(None)
  def getByXid(xid:String):Future[Option[User]] = Future.successful(None)
  def getByXidAlways(xid:String):Future[Option[User]] = Future.successful(None)
  def all():Future[Users] = Future.successful(Users(Seq()))
}
