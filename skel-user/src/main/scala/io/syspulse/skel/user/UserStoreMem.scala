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
  
  var users: Map[UUID,User] = Map()

  def all:Seq[User] = users.values.toSeq

  def size:Long = users.size

  def +(user:User):Try[UserStore] = { users = users + (user.id -> user); Success(this)}

  def del(id:UUID):Try[UserStore] = { 
    val sz = users.size
    users = users - id;
    if(sz == users.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def -(user:User):Try[UserStore] = { 
    del(user.id)
  }

  def ?(id:UUID):Option[User] = users.get(id)

  def findByEid(eid:String):Option[User] = {
    users.values.find(_.eid == eid)
  }
}
