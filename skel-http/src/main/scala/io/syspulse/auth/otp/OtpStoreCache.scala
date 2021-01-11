package io.syspulse.auth.otp

import scala.util.Try

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._


class OtpStoreCache extends OtpStore {
  var otps: Set[Otp] = Set()

  def getAll:Seq[Otp] = otps.toSeq
  def size:Long = otps.size

  def +(otp:Otp):OtpStore = { otps = otps + otp; this}
  def -(id:UUID):OtpStore = { 
    otps.find(_.id == id) match {
      case Some(otp) => otps = otps - otp;
      case None =>
    }
    this
  }
  def -(otp:Otp):OtpStore = { otps = otps - otp; this }

  def get(id:UUID):Option[Otp] = otps.find(_.id == id)
}
