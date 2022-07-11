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
  val system: ActorSystem[Command] = ActorSystem(EnrollFlow(), "EnrollSystem")
  implicit val sched = system.scheduler
  implicit val timeout =  Timeout(3.seconds)

  def start(flow:String,xid:Option[String] = None):UUID = {
    val eid = UUID.random
    system ! EnrollFlow.StartFlow(eid,flow,xid,system.ignoreRef)
    eid
  }

  def findEnroll(eid:UUID):Option[ActorRef[Command]] = {
    
    val enrollActor = Await.result(
      system.ask {
        ref => EnrollFlow.FindFlow(eid, ref)
      }, timeout.duration)

    log.info(s"enrollActor = ${enrollActor}")
    enrollActor
  }

  def summary(eid:UUID):Option[Enroll.Summary] = {
    
    val summary = Await.result(
      system.ask {
        ref => EnrollFlow.GetSummary(eid, ref)
      }, timeout.duration)

    log.info(s"summary = ${summary}")
    summary
  }
  
  def sendEmailConfirmation(eid:UUID,confirmCode:String):Unit = {    
    system ! EnrollFlow.ConfirmEmail(eid,confirmCode)
  }

  def addEmail(eid:UUID,email:String):Unit = {    
    system ! EnrollFlow.AddEmail(eid,email)
  }
  
}