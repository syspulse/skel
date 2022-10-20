package io.syspulse.skel.user

import scala.collection.immutable

import io.jvm.uuid._

final case class User(id:UUID, email:String = "", name:String = "", xid:String = "", tsCreated:Long = System.currentTimeMillis())
final case class Users(users: immutable.Seq[User])

final case class UserCreateReq(email: String, name:String, xid: String, uid:Option[UUID] = None)
final case class UserRandomReq()

final case class UserActionRes(status: String,id:Option[UUID])

final case class UserRes(user: Option[User])