package io.syspulse.skel.user.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

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

  def +(user:User):Try[UserStore] = { 
    users = users + (user.id -> user)
    log.info(s"add: ${user}")
    Success(this)
  }

  def del(id:UUID):Try[UserStore] = { 
    val sz = users.size
    users = users - id;
    log.info(s"del: ${id}")
    if(sz == users.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
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
}
