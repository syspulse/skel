package io.syspulse.skel.otp.store

import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.otp.Otp

class OtpStoreMem extends OtpStore {
  val log = Logger(s"${this}")

  var otps: Set[Otp] = Set()

  def all: Future[Seq[Otp]] = Future.successful(otps.toSeq)

  def getForUser(uid:UUID): Future[Seq[Otp]] = {
    Future.successful(otps.filter(_.uid == uid).toSeq)
  }

  def size: Future[Long] = Future.successful(otps.size.toLong)

  def +(otp:Otp): Future[Otp] = { otps = otps + otp; Future.successful(otp) }

  def del(id:UUID): Future[UUID] = {
    otps.find(_.id == id) match {
      case Some(otp) => otps = otps - otp; Future.successful(id)
      case None => Future.failed(new Exception(s"not found: ${id}"))
    }
  }

  def ?(id:UUID): Future[Otp] = otps.find(_.id == id) match {
    case Some(o) => Future.successful(o)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }
}
