package io.syspulse.skel.user.store

import scala.collection.immutable
import scala.concurrent.{Future, ExecutionContext}

import io.jvm.uuid._

import io.syspulse.skel.user._
import io.syspulse.skel.store.Store

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserUpdateReq

object UserStore {
  // Minimal number of characters required to run a free-text search.
  val SEARCH_MIN_LEN = 3
}

trait UserStore extends Store[User, UUID] {

  def getKey(e: User): UUID = e.id
  def +(user: User): Future[User]
  def del(id: UUID): Future[UUID]
  def ?(id: UUID): Future[User]
  def all: Future[Seq[User]]
  def ???(from: Long, size: Long)(implicit ec: ExecutionContext): Future[Seq[User]] =
    all.map(users => page(users, from, size))
  def size: Future[Long]

  def findByXid(xid: String): Future[Option[User]]
  def findByEmail(email: String): Future[Option[User]]
  def update(id: UUID, req: UserUpdateReq): Future[User]

  def search(query: String): Future[Seq[User]]

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

  /** In-memory slice: `drop(from).take(size)`. */
  protected def page(users: Seq[User], from: Long, size: Long): Seq[User] =
    users.drop(from.max(0).toInt).take(size.max(0).toInt)
}
