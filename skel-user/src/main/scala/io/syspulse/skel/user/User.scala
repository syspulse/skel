package io.syspulse.skel.user

import scala.collection.immutable

import io.jvm.uuid._

final case class User(id:UUID, email:String = "", name:String = "", xid:String = "", avatar:String = "",tsCreated:Long = System.currentTimeMillis())
