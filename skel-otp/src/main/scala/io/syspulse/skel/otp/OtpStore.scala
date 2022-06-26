package io.syspulse.skel.otp

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait OtpStore extends Store[Otp,UUID] {
  
  def +(otp:Otp):Try[OtpStore]
  def -(otp:Otp):Try[OtpStore]
  def del(id:UUID):Try[OtpStore]
  def get(id:UUID):Option[Otp]
  def getAll:Seq[Otp]
  def getForUser(userId:UUID):Seq[Otp]
  def size:Long
}

