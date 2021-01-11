package io.syspulse.auth.otp

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

trait OtpStore  {
  
  def +(otp:Otp):Try[OtpStore]
  def -(otp:Otp):Try[OtpStore]
  def -(id:UUID):Try[OtpStore]
  def get(id:UUID):Option[Otp]
  def getAll:Seq[Otp]
  def size:Long
}

