package io.syspulse.auth.otp

import scala.collection.immutable

import io.jvm.uuid._

final case class Otp(id:UUID, secret: String,name:String, uri:String, period:Int)