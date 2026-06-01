package io.syspulse.skel.user.server

import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.user.User

final case class Users(users: immutable.Seq[User])

/** Create user — `email` required; other fields optional. */
final case class UserCreateReq(
  email: String,
  name: Option[String] = None,
  xid: Option[String] = None,
  avatar: Option[String] = None,
  meta: Option[Map[String, Any]] = None,
  uid: Option[UUID] = None,
)

/** Update user — only provided fields are changed. */
final case class UserUpdateReq(
  email: Option[String] = None,
  name: Option[String] = None,
  xid: Option[String] = None,
  avatar: Option[String] = None,
  meta: Option[Map[String, Any]] = None,
)

final case class UserRandomReq()

final case class UserActionRes(status: String, uid: Option[UUID])

final case class UserRes(user: Option[User])

final case class UserUploadRes(status: String, uid: Option[UUID], uri: String, file: Option[String] = None)
