package io.syspulse.skel.user.store

import scala.util.Try
import scala.concurrent.Future

import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserUpdateReq

class UserStoreDBSync(configuration: Configuration, dbConfigRef: String) extends UserStore {

  private val log = Logger(getClass)

  private val store = new UserStoreDBAsync(configuration, dbConfigRef)

  def all: Seq[User]                                                  = store.all
  def size: Long                                                      = store.size
  override def ???(from: Long, size: Long): Seq[User]                  = store.???(from, size)

  def +(user: User): Try[User]                                        = store.+(user)
  def del(id: UUID): Try[UUID]                                        = store.del(id)
  def ?(id: UUID): Try[User]                                          = store.?(id)
  def update(id: UUID, req: UserUpdateReq): Try[User]                 = store.update(id, req)
  def findByXid(xid: String): Option[User]                           = store.findByXid(xid)
  def findByEmail(email: String): Option[User]                        = store.findByEmail(email)

  override def allAsync: Future[Seq[User]]                            = store.allAsync
  override def pageAsync(from: Long, size: Long): Future[Seq[User]]  = store.pageAsync(from, size)
  override def sizeAsync: Future[Long]                                = store.sizeAsync
  override def +!(user: User): Future[User]                           = store.+!(user)
  override def delAsync(id: UUID): Future[UUID]                       = store.delAsync(id)
  override def ?!(id: UUID): Future[User]                             = store.?!(id)
  def updateAsync(id: UUID, req: UserUpdateReq): Future[User]         = store.updateAsync(id, req)
  def findByXidAsync(xid: String): Future[User]                      = store.findByXidAsync(xid)
  def findByEmailAsync(email: String): Future[User]                   = store.findByEmailAsync(email)
}
