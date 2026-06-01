package io.syspulse.skel.user.store

import scala.util.{Try, Success, Failure}
import scala.collection.immutable
import scala.concurrent.Future

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserUpdateReq
import io.syspulse.skel.ErrNotFound

class UserStoreMem extends UserStore {
  val log = Logger(s"${this}")

  var users: Map[UUID, User] = Map()

  def all: Seq[User] = users.values.toSeq
  def size: Long = users.size

  def +(user: User): Try[User] = {
    users = users + (user.id -> user)
    log.info(s"add: ${user}")
    Success(user)
  }

  def del(id: UUID): Try[UUID] = {
    val sz = users.size
    users = users - id
    log.info(s"del: ${id}")
    if (sz == users.size) Failure(new ErrNotFound(s"${id}")) else Success(id)
  }

  def ?(id: UUID): Try[User] = users.get(id) match {
    case Some(u) => Success(u)
    case None    => Failure(new ErrNotFound(s"${id}"))
  }

  def findByXid(xid: String): Option[User] = {
    users.values.find(u => u.xid.exists(_.equalsIgnoreCase(xid)))
  }

  def findByEmail(email: String): Option[User] = {
    users.values.find(_.email.equalsIgnoreCase(email))
  }

  def update(id: UUID, req: UserUpdateReq): Try[User] = {
    this.?(id) match {
      case Success(user) =>
        val user1 = applyUpdate(user, req)
        this.+(user1)
        Success(user1)
      case f => f
    }
  }

  def findByXidAsync(xid: String): Future[User] = throw new NotImplementedError()
  def findByEmailAsync(email: String): Future[User] = throw new NotImplementedError()
  def updateAsync(id: UUID, req: UserUpdateReq): Future[User] = throw new NotImplementedError()
}
