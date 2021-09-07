package io.syspulse.skel.otp

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class OtpStoreMem extends OtpStore {
  val log = Logger(s"${this}")
  
  var otps: Set[Otp] = Set()

  def getAll:Seq[Otp] = otps.toSeq

  def getForUser(userId:UUID):Seq[Otp] = {
    otps.filter(_.userId == userId).toSeq
  }

  def size:Long = otps.size

  def +(otp:Otp):Try[OtpStore] = { otps = otps + otp; Success(this)}
  def -(id:UUID):Try[OtpStore] = { 
    otps.find(_.id == id) match {
      case Some(otp) => { otps = otps - otp; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(otp:Otp):Try[OtpStore] = { 
    val sz = otps.size
    otps = otps - otp;
    if(sz == otps.size) Failure(new Exception(s"not found: ${otp}")) else Success(this)
  }

  def get(id:UUID):Option[Otp] = otps.find(_.id == id)
}
