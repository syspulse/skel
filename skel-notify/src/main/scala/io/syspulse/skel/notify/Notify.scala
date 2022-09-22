package io.syspulse.skel.notify

import scala.collection.immutable

import io.jvm.uuid._

final case class Notify(id:UUID, email:String = "", name:String = "", eid:String = "", tsCreated:Long = System.currentTimeMillis())
final case class Notifys(notifys: immutable.Seq[Notify])

final case class NotifyCreateReq(email: String, name:String, eid: String, uid:Option[UUID] = None)
final case class NotifyRandomReq()

final case class NotifyActionRes(status: String,id:Option[UUID])

final case class NotifyRes(id: Option[Notify])