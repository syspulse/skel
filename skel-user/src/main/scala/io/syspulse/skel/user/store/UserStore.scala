package io.syspulse.skel.user.store

import scala.util.Try

import scala.collection.immutable
import scala.concurrent.Future

import io.jvm.uuid._

import io.syspulse.skel.user._
import io.syspulse.skel.store.Store

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserUpdateReq

trait UserStore extends Store[User, UUID] {

  def getKey(e: User): UUID = e.id
  def +(user: User): Try[User]
  def del(id: UUID): Try[UUID]
  def ?(id: UUID): Try[User]
  def all: Seq[User]
  def size: Long

  def findByXid(xid: String): Option[User]
  def findByEmail(email: String): Option[User]
  def update(id: UUID, req: UserUpdateReq): Try[User]

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

  def updateAsync(id: UUID, req: UserUpdateReq): Future[User]
  def findByXidAsync(xid: String): Future[User]
  def findByEmailAsync(email: String): Future[User]
}
