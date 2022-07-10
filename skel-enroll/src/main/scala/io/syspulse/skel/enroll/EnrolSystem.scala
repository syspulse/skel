package io.syspulse.skel.enroll

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
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior

import io.jvm.uuid._

import io.syspulse.skel.crypto.key.{PK,Signature}
import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.SignatureEth
import akka.util.Timeout
import scala.concurrent.Await

import io.syspulse.skel.enroll.Command

object EnrollSystem {
  val log = Logger(s"${this}")
  val system: ActorSystem[Command] = ActorSystem(EnrollManager(), "EnrollSystem")

  def start(flow:String,xid:Option[String] = Some("XID-0000001")):UUID = {
    val eid = UUID.random
    system ! EnrollManager.StartFlow(eid,flow,xid,system.ignoreRef)
    eid
  }

  def findFlow(eid:UUID):Option[ActorRef[Command]] = {
    implicit val timeout =  Timeout(3.seconds)
    implicit val sched = system.scheduler
    val enrollActor = Await.result(
      system.ask {
        ref => EnrollManager.FindFlow(eid, ref)
      }, timeout.duration)

    log.info(s"enrollActor = ${enrollActor}")
    enrollActor
  }

  def sendEmailConfirmation(eid:UUID,confirmCode:String):Unit = {    
    system ! EnrollManager.ConfirmEmailFlow(eid,confirmCode)
  }
  
}