package io.syspulse.skel.otp

import scala.collection.immutable

import io.jvm.uuid._

final case class Otp(id:UUID, uid:UUID, secret: String,name:String, account:String, issuer:String="", period:Int = 30, digits:Int = 6,algo:String = "SHA1")
