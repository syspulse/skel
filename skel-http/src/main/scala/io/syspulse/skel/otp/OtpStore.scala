package io.syspulse.skel.otp

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait OtpStore extends Store[Otp] {
  
  def +(otp:Otp):Try[OtpStore]
  def -(otp:Otp):Try[OtpStore]
  def -(id:UUID):Try[OtpStore]
  def get(id:UUID):Option[Otp]
  def getAll:Seq[Otp]
  def size:Long
}

