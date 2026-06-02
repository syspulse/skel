package io.syspulse.skel.user.store

import scala.util.{Try, Success, Failure}
import scala.collection.immutable
import scala.concurrent.{Future, ExecutionContext}

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserJson._

// Preload from file during start
class UserStoreDir(dir: String = "store/") extends StoreDir[User, UUID](dir) with UserStore {
  val store = new UserStoreMem

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def toKey(id: String): UUID = UUID(id)
  def all: Future[Seq[User]] = store.all
  override def list(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[UserStore.Page] =
    store.list(from, size)
  override def ???(from: Long, size: Long)(implicit ec: ExecutionContext): Future[Seq[User]] = store.???(from, size)
  def size: Future[Long] = store.size
  override def +(u: User): Future[User] = super.+(u).flatMap(_ => store.+(u))

  override def del(uid: UUID): Future[UUID] = super.del(uid).flatMap(_ => store.del(uid))
  override def ?(uid: UUID): Future[User] = store.?(uid)

  override def findByXid(xid: String): Future[Option[User]] = store.findByXid(xid)
  override def findByEmail(email: String): Future[Option[User]] = store.findByEmail(email)
  override def update(id: UUID, req: io.syspulse.skel.user.server.UserUpdateReq): Future[User] =
    store.update(id, req).flatMap(u => Future.fromTry(writeFile(u)))

  override def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[UserStore.Page] =
    store.search(query, from, size)

  // preload and watch
  load(dir)
  watch(dir)
}
