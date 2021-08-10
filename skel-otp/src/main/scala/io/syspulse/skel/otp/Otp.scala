package io.syspulse.skel.otp

import scala.collection.immutable

import io.jvm.uuid._

final case class Otp(id:UUID, userId:UUID, secret: String,name:String, uri:String, period:Int = 30, digits:Int = 6,algo:String = "SHA1")