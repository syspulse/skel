package io.syspulse.skel.user.store

import scala.collection.immutable
import scala.concurrent.{Future, ExecutionContext}
import java.util.regex.Pattern

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserUpdateReq
import io.syspulse.skel.ErrNotFound

class UserStoreMem extends UserStore {
  val log = Logger(s"${this}")

  var users: Map[UUID, User] = Map()

  def all: Future[Seq[User]] = Future.successful(users.values.toSeq)
  def size: Future[Long] = Future.successful(users.size.toLong)

  override def list(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[UserStore.Page] = {
    val allUsers = users.values.toSeq
    val total = allUsers.size.toLong
    val pageUsers = (from, size) match {
      case (Some(f), Some(s)) => page(allUsers, f, s)
      case (None, None)       => allUsers
      case _                  => allUsers
    }
    Future.successful(UserStore.Page(pageUsers, total))
  }

  def +(user: User): Future[User] = {
    users = users + (user.id -> user)
    log.info(s"add: ${user}")
    Future.successful(user)
  }

  def del(id: UUID): Future[UUID] = {
    val sz = users.size
    users = users - id
    log.info(s"del: ${id}")
    if (sz == users.size) Future.failed(new ErrNotFound(s"${id}")) else Future.successful(id)
  }

  def ?(id: UUID): Future[User] = users.get(id) match {
    case Some(u) => Future.successful(u)
    case None    => Future.failed(new ErrNotFound(s"${id}"))
  }

  def findByXid(xid: String): Future[Option[User]] = {
    Future.successful(users.values.find(u => u.xid.exists(_.equalsIgnoreCase(xid))))
  }

  def findByEmail(email: String): Future[Option[User]] = {
    Future.successful(users.values.find(_.email.equalsIgnoreCase(email)))
  }

  def findByData(path: String, value: String): Future[Seq[User]] =
    Future.successful(
      users.values.filter(u => u.data.flatMap(UserStore.jsonPathText(_, path)).contains(value)).toSeq,
    )

  def update(id: UUID, req: UserUpdateReq): Future[User] = {
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
    ?(id).map { user =>
      val user1 = applyUpdate(user, req)
      users = users + (user1.id -> user1)
      user1
    }
  }

  private def regexpMatch(pattern: Pattern, text: String): Boolean =
    pattern.matcher(text).find()

  def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[UserStore.Page] = {
    val q = //UserStore.normalizeSearchQuery(query)
      query  

    if (q.length < UserStore.SEARCH_MIN_LEN) return Future.successful(UserStore.Page(Seq.empty, 0))

    val pattern =
      try Pattern.compile(q, Pattern.CASE_INSENSITIVE)
      catch { case _: Exception => return Future.failed(new Exception(s"invalid regex: '${q}'")) }

    val matched = users.values.filter { u =>
      regexpMatch(pattern, u.email) ||
        u.name.exists(regexpMatch(pattern, _)) ||
        u.xid.exists(regexpMatch(pattern, _))
    }.toSeq

    val total = matched.size.toLong
    val pageUsers = (from, size) match {
      case (Some(f), Some(s)) => page(matched, f, s)
      case (None, None)       => matched
      case _                  => matched
    }
    Future.successful(UserStore.Page(pageUsers, total))
  }
}
