package io.syspulse.skel.user.store

import scala.collection.immutable
import scala.concurrent.{Future, ExecutionContext}

import io.jvm.uuid._

import io.syspulse.skel.user._
import io.syspulse.skel.store.Store

import spray.json._

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.UserUpdateReq
import io.syspulse.skel.store.StoreFts

object UserStore {
  val SEARCH_MIN_LEN = StoreFts.SEARCH_MIN_LEN

  final case class Page(users: Seq[User], total: Long)

  def normalizeSearchQuery(query: String): String = StoreFts.normalizeSearchQuery(query)
  def tokenizeSearchField(text: String): Seq[String] = StoreFts.tokenizeSearchField(text)
  def postgresSearchTerms(query: String): Seq[String] = StoreFts.postgresSearchTerms(query)
  def postgresPrefixTsQuery(query: String): Option[String] = StoreFts.postgresPrefixTsQuery(query)

  /** Extract a text value from a `JsObject` using a dot-separated path (e.g. `profile.tier`). */
  def jsonPathText(obj: JsObject, path: String): Option[String] =
    path.split("\\.").filter(_.nonEmpty).foldLeft(Option[JsValue](obj): Option[JsValue]) { (cur, key) =>
      cur.flatMap {
        case o: JsObject => o.fields.get(key)
        case _           => None
      }
    }.flatMap {
      case JsString(s)  => Some(s)
      case JsNumber(n)  => Some(n.toString)
      case JsBoolean(b) => Some(b.toString)
      case JsNull       => Some("null")
      case _            => None
    }
}

trait UserStore extends Store[User, UUID] {

  def getKey(e: User): UUID = e.id
  def +(user: User): Future[User]
  def del(id: UUID): Future[UUID]
  def ?(id: UUID): Future[User]
  def all: Future[Seq[User]]
  def ???(from: Long, size: Long)(implicit ec: ExecutionContext): Future[Seq[User]] =
    list(Some(from), Some(size)).map(_.users)
  def list(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[UserStore.Page] =
    all.map { users =>
      val total = users.size.toLong
      val pageUsers = (from, size) match {
        case (Some(f), Some(s)) => page(users, f, s)
        case (None, None)       => users
        case _                  => users
      }
      UserStore.Page(pageUsers, total)
    }
  def size: Future[Long]

  def findByXid(xid: String): Future[Option[User]]
  def findByEmail(email: String): Future[Option[User]]
  /** Find users where `data` field has `path` equal to `value` (dot-separated path). */
  def findByData(path: String, value: String): Future[Seq[User]]
  def update(id: UUID, req: UserUpdateReq): Future[User]

  def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[UserStore.Page]

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
