package io.syspulse.skel.enroll.flow

import java.time.Instant
import scala.util.Random
import com.typesafe.scalalogging.Logger
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._

import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId

import java.time.Instant
import scala.util.Random
import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.pattern.StatusReply

import io.jvm.uuid._

import io.syspulse.skel.crypto.key.{PK,Signature}
import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.SignatureEth
import akka.util.Timeout
import scala.concurrent.Await


import io.syspulse.skel.enroll._

object Enrollment {
  final case class Summary(
    eid:UUID, 
    phase:String = "START", 
    xid:Option[String] = None, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None, 
    addr:Option[String] = None, sig:Option[Signature] = None,
    tsStart:Long = 0L, tsPhase:Long = 0L, 
    finished: Boolean = false, 
    confirmToken:Option[String] = None,
    uid:Option[UUID] = None) extends CborSerializable

  final case class State(eid:UUID, flow:Seq[String], phase:String = "START", 
    xid:Option[String] = None,
    email:Option[String] = None,
    name:Option[String] = None, 
    avatar:Option[String] = None,
    pk:Option[String] = None, sig:Option[String] = None,
    uid:Option[UUID] = None,
    tsStart:Long = System.currentTimeMillis, 
    tsPhase:Long = System.currentTimeMillis,
    finished:Boolean = false,
    confirmToken:Option[String] = None,
    data:Map[String,String] = Map()) extends CborSerializable {

    def isFinished: Boolean = finished

    def nextPhase(phase: String): State = copy(phase = phase)

    def updatePhase(phase:String):State=copy(phase=phase,tsPhase=System.currentTimeMillis())
    def start(xid:String,email:String,name:String,avatar:String):State=copy(phase="START_ACK",xid=Some(xid),email=Some(email),name=Some(name),avatar=Some(avatar),tsPhase=System.currentTimeMillis())
    def addEmail(email:String,token:String):State=copy(phase="EMAIL_ACK",email=Some(email),tsPhase=System.currentTimeMillis(),confirmToken=Some(token))
    def confirmEmail(): State = copy(phase = "CONFIRM_EMAIL_ACK",tsPhase=System.currentTimeMillis())
    def addPublicKey(pk:PK,sig:SignatureEth): State = copy(phase = "PK_ACK", pk = Some(Util.hex(pk)), sig = Some(Util.hex(sig.toArray())), tsPhase=System.currentTimeMillis())
    def createUser(uid:UUID): State = copy(phase = "CREATE_USER_ACK", uid=Some(uid), tsPhase=System.currentTimeMillis())
    
    def finish(now: Instant): State = copy(phase = "FINISH_ACK", tsPhase=now.getEpochSecond(), finished = true)

    def addData(k: String, v:String): State = copy(data = data + (k -> v))
    
    def toSummary: Summary = Summary(eid, phase, xid, email, name, avatar, pk.map(Eth.address(_)), sig, tsStart,tsPhase,finished,confirmToken,uid)

    //def toByteArray() = toString.getBytes()
  }

  object State {
    def apply(eid:UUID,flow:String) = new State(eid,flow.split(",").map(_.trim.toUpperCase()))
  }

  final case class Start(eid:UUID,flow:String,xid:String,name:String,email:String,avatar:String,replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class AddEmail(email: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class ConfirmEmail(token: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class AddPublicKey(sig:SignatureEth, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class CreateUser(uid:UUID,replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Continue(replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class Finish(replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command
  final case class UpdatePhase(phase:String,replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Info(replyTo: ActorRef[State]) extends Command
}

abstract class Enrollment {
  val log = Logger(s"${this}")

  import Enrollment._
  
  def generateSigData(eid:UUID,email:String):String = {
    val tsSig = System.currentTimeMillis() / 5000L
    val data = s"${tsSig},${eid},${email}"
    data
  }
  
  def apply(eid:UUID = UUID.random, flow:String = ""): Behavior[Command] = ???
}
