package io.syspulse.skel.user

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class UserStoreMem extends UserStore {
  val log = Logger(s"${this}")
  
  var users: Set[User] = Set()

  def all:Seq[User] = users.toSeq

  def size:Long = users.size

  def +(user:User):Try[UserStore] = { users = users + user; Success(this)}

  def del(id:UUID):Try[UserStore] = { 
    users.find(_.id == id) match {
      case Some(user) => { users = users - user; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(user:User):Try[UserStore] = { 
    val sz = users.size
    users = users - user;
    if(sz == users.size) Failure(new Exception(s"not found: ${user}")) else Success(this)
  }

  def ?(id:UUID):Option[User] = users.find(_.id == id)
}
