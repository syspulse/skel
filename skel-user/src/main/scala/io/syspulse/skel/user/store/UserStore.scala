package io.syspulse.skel.user.store

import scala.util.Try

import scala.collection.immutable
import scala.concurrent.Future

import io.jvm.uuid._

import io.syspulse.skel.user._
import io.syspulse.skel.store.Store

import io.syspulse.skel.user.User

trait UserStore extends Store[User,UUID] {
  
  def getKey(e: User): UUID = e.id
  def +(user:User):Try[User]
  //def -(user:User):Try[User]
  def del(id:UUID):Try[UUID]
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
  
  // def +!(user:User):Future[User]  
  // def ?!(id:UUID):Future[User]
  // def allAsync:Future[Seq[User]]
  // def sizeAsync:Future[Long]
  
  def updateAsync(id:UUID, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):Future[User]
  def findByXidAsync(xid:String):Future[User]
  def findByEmailAsync(email:String):Future[User]    
}

