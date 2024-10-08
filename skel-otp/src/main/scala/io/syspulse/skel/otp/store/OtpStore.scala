package io.syspulse.skel.otp.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

import io.syspulse.skel.otp.Otp

trait OtpStore extends Store[Otp,UUID] {
  def getKey(o: Otp): UUID = o.id
  
  def +(otp:Otp):Try[Otp]
  
  def del(id:UUID):Try[UUID]
  def ?(id:UUID):Try[Otp]
  def all:Seq[Otp]
  
  def getForUser(uid:UUID):Seq[Otp]
  def size:Long
}

