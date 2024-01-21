package io.syspulse.skel.user.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.user._
import io.syspulse.skel.store.Store

import io.syspulse.skel.user.User
import io.syspulse.skel.store.StoreAsync

trait UserStoreAsync extends StoreAsync[User,UUID] {
  
  def getKey(e: User): UUID = e.id
  
  def +(user:User):Future[UserStoreAsync]
  
  def del(id:UUID):Future[UserStoreAsync]
  def ?(id:UUID):Future[User]  
  def all:Future[Seq[User]]
  def size:Future[Long]

  def findByXid(xid:String):Future[User]
  def findByEmail(email:String):Future[User]

  def update(id:UUID, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):Future[User]

  protected def modify(user:User, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):User = {    
    (for {
      u0 <- Some(user)
      u1 <- Some(if(email.isDefined) u0.copy(email = email.get) else u0)
      u2 <- Some(if(name.isDefined) u1.copy(name = name.get) else u1)
      u3 <- Some(if(avatar.isDefined) u2.copy(avatar = avatar.get) else u2)
    } yield u3).get    
  }
}

