package io.syspulse.skel.user.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.user._
import io.syspulse.skel.store.Store

trait UserStore extends Store[User,UUID] {
  
  def +(user:User):Try[UserStore]
  def -(user:User):Try[UserStore]
  def del(id:UUID):Try[UserStore]
  def ?(id:UUID):Try[User]
  
  def all:Seq[User]
  def size:Long

  def findByXid(xid:String):Option[User]
  def findByEmail(email:String):Option[User]

  def update(id:UUID, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):Try[User]

  protected def modify(user:User, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):User = {    
    (for {
      u0 <- Some(user)
      u1 <- Some(if(email.isDefined) u0.copy(email = email.get) else u0)
      u2 <- Some(if(name.isDefined) u1.copy(name = name.get) else u1)
      u3 <- Some(if(avatar.isDefined) u2.copy(avatar = avatar.get) else u2)
    } yield u3).get    
  }
}

