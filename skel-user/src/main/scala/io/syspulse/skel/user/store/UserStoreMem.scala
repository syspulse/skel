package io.syspulse.skel.user.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.user.User

class UserStoreMem extends UserStore {
  val log = Logger(s"${this}")
  
  var users: Map[UUID,User] = Map()

  def all:Seq[User] = users.values.toSeq

  def size:Long = users.size

  def +(user:User):Try[User] = { 
    users = users + (user.id -> user)
    log.info(s"add: ${user}")
    Success(user)
  }

  def del(id:UUID):Try[UUID] = { 
    val sz = users.size
    users = users - id;
    log.info(s"del: ${id}")
    if(sz == users.size) Failure(new Exception(s"not found: ${id}")) else Success(id)  
  }

  // def -(user:User):Try[UserStore] = {     
  //   del(user.id)
  // }

  def ?(id:UUID):Try[User] = users.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def findByXid(xid:String):Option[User] = {
    users.values.find(_.xid.toLowerCase == xid.toLowerCase())
  }

  def findByEmail(email:String):Option[User] = {
    users.values.find(_.email.toLowerCase == email.toLowerCase)
  }

  def update(id:UUID,email:Option[String]=None,name:Option[String]=None,avatar:Option[String]=None):Try[User] = {
    this.?(id) match {
      case Success(user) => 
        val user1 = modify(user,email,name,avatar)
        this.+(user1)
        Success(user1)
      case f => f
    }
  }

  // Async not implemented
  // def +!(user:User):Future[User] = throw new NotImplementedError()
  // def delAsync(id:UUID):Future[UUID] = throw new NotImplementedError()
  // def ?!(id:UUID):Future[User] = throw new NotImplementedError()
  // def allAsync:Future[Seq[User]] = throw new NotImplementedError()
  // def sizeAsync:Future[Long] = throw new NotImplementedError()
  def findByXidAsync(xid:String):Future[User] = throw new NotImplementedError()
  def findByEmailAsync(email:String):Future[User] = throw new NotImplementedError()
  def updateAsync(id:UUID, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):Future[User] = throw new NotImplementedError()
}
