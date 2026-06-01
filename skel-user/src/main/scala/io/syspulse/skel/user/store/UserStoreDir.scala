package io.syspulse.skel.user.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future

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

  def toKey(id:String):UUID = UUID(id)
  def all:Seq[User] = store.all
  override def ???(from: Long, size: Long): Seq[User] = store.???(from, size)
  def size:Long = store.size
  override def +(u:User):Try[User] = super.+(u).flatMap(_ => store.+(u))

  override def del(uid:UUID):Try[UUID] = super.del(uid).flatMap(_ => store.del(uid))
  override def ?(uid:UUID):Try[User] = store.?(uid)

  override def findByXid(xid:String):Option[User] = store.findByXid(xid)
  override def findByEmail(email:String):Option[User] = store.findByEmail(email)
  override def update(id: UUID, req: io.syspulse.skel.user.server.UserUpdateReq): Try[User] =
    store.update(id, req).flatMap(u => writeFile(u))

  // preload and watch
  load(dir)
  watch(dir)
 
  // Async not implemented
  // def +!(user:User):Future[User] = throw new NotImplementedError()
  // def delAsync(id:UUID):Future[UUID] = throw new NotImplementedError()
  // def ?!(id:UUID):Future[User] = throw new NotImplementedError()
  // def allAsync:Future[Seq[User]] = throw new NotImplementedError()
  // def sizeAsync:Future[Long] = throw new NotImplementedError()
  
  def findByXidAsync(xid:String):Future[User] = throw new NotImplementedError()
  def findByEmailAsync(email:String):Future[User] = throw new NotImplementedError()
  def updateAsync(id: UUID, req: io.syspulse.skel.user.server.UserUpdateReq): Future[User] =
    throw new NotImplementedError()
}