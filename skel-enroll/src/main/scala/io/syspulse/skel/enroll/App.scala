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

object App {
  val log = Logger(s"${this}")  
  val system: ActorSystem[Command] = ActorSystem(EnrollFlow(), "EnrollSystem")

  def main(args:Array[String]):Unit = {
    val eid = UUID.random
    val actor = system ! EnrollFlow.StartFlow(eid,"START,STARTED,EMAIL,CONFIRM_EMAIL,EMAIL_CONFIRMED,CREATE_USER,USER_CREATED,FINISH,FINISHED",None,system.ignoreRef)
    println(eid)
  } 
  
}