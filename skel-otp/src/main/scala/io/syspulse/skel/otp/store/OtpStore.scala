package io.syspulse.skel.otp.store

import scala.concurrent.Future

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

import io.syspulse.skel.otp.Otp

trait OtpStore extends Store[Otp,UUID] {
  def getKey(o: Otp): UUID = o.id

  def +(otp:Otp):Future[Otp]

  def del(id:UUID):Future[UUID]
  def ?(id:UUID):Future[Otp]
  def all:Future[Seq[Otp]]

  def getForUser(uid:UUID):Future[Seq[Otp]]
  def size:Future[Long]
}

