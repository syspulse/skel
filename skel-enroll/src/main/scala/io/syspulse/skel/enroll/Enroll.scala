package io.syspulse.skel.enroll

import scala.collection.immutable

import io.jvm.uuid._

final case class Enroll(id:UUID, email:String = "", name:String = "", xid:String = "", tsCreated:Long, phase:String="")
final case class Enrolls(enrolls: immutable.Seq[Enroll])

final case class EnrollCreateReq(email: Option[String] = None, name:Option[String]=None, xid: Option[String]=None)

final case class EnrollActionRes(status: String,id:Option[UUID])

final case class EnrollRes(enroll: Option[Enroll])