package io.syspulse.skel.enroll

import scala.collection.immutable

import io.jvm.uuid._

final case class Enroll(id:UUID, email:String = "", name:String = "", xid:String = "", tsCreated:Long)
final case class Enrolls(enrolls: immutable.Seq[Enroll])

final case class EnrollCreateReq(email: String, name:String, xid: String)

final case class EnrollActionRes(status: String,id:Option[UUID])

final case class EnrollRes(enroll: Option[Enroll])