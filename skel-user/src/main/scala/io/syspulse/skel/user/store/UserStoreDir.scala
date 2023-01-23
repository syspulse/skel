package io.syspulse.skel.user.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserJson._

// Preload from file during start
class UserStoreDir(dir:String = "store/") extends StoreDir[User,UUID](dir) with UserStore {
  val store = new UserStoreMem

  def all:Seq[User] = store.all
  def size:Long = store.size
  override def +(u:User):Try[UserStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)

  override def del(uid:UUID):Try[UserStoreDir] = super.del(uid).flatMap(_ => store.del(uid)).map(_ => this)
  override def ?(uid:UUID):Try[User] = store.?(uid)

  override def findByXid(xid:String):Option[User] = store.findByXid(xid)
  override def findByEmail(email:String):Option[User] = store.findByEmail(email)
  override def update(id:UUID, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):Try[User] = 
    store.update(id,email,name,avatar).flatMap(u => writeFile(u))

  // preload
  load(dir)
}