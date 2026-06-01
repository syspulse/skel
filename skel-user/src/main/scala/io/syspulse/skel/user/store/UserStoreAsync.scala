package io.syspulse.skel.user.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.user._
import io.syspulse.skel.store.Store

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserUpdateReq
import io.syspulse.skel.store.StoreAsync

trait UserStoreAsync extends StoreAsync[User, UUID] {

  def getKey(e: User): UUID = e.id

  def +(user: User): Future[UserStoreAsync]

  def del(id: UUID): Future[UserStoreAsync]
  def ?(id: UUID): Future[User]
  def all: Future[Seq[User]]
  def size: Future[Long]

  def findByXid(xid: String): Future[User]
  def findByEmail(email: String): Future[User]

  def update(id: UUID, req: UserUpdateReq): Future[User]

  protected def applyUpdate(user: User, req: UserUpdateReq): User = {
    val now = System.currentTimeMillis()
    user.copy(
      email = req.email.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(user.email),
      name = req.name.filter(_.nonEmpty).orElse(user.name),
      xid = req.xid.filter(_.nonEmpty).orElse(user.xid),
      avatar = req.avatar.filter(_.nonEmpty).orElse(user.avatar),
      meta = req.meta.orElse(user.meta),
      ts = now,
    )
  }
}
