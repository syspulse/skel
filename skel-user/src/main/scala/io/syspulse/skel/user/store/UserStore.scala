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
  def ?(id:UUID):Option[User]
  def all:Seq[User]
  def size:Long

  def findByXid(xid:String):Option[User]
}

