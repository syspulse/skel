package io.syspulse.skel.enroll.state

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
import akka.persistence.typed.state.scaladsl.DurableStateBehavior
import akka.persistence.typed.state.scaladsl.Effect
import akka.persistence.typed.state.RecoveryCompleted

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

import io.syspulse.skel.enroll.Enroll
import io.syspulse.skel.enroll._

object EnrollState extends Enroll {
  import io.syspulse.skel.enroll.Enroll._
  
  override def apply(eid:UUID = UUID.random, flow:String = ""): Behavior[Command] =  Behaviors.setup { ctx => {
    DurableStateBehavior[Command, State](
      persistenceId = PersistenceId("Enroll", eid.toString()),
      emptyState = State(eid,flow),
      commandHandler = (state, command) => {
        if (state.isFinished) 
          finishedEnroll(eid, state, command)
        else {
          if(flow.isEmpty())
            startNewEnroll(eid, state, command)
          else
            startAutoflowEnroll(eid,flow,state,command,ctx)
        }
      })
      .receiveSignal {
        case (state, RecoveryCompleted) =>
          log.info(s"RECOVERY: ${eid}: ${state}")
      }      
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }
  }

  private def startAutoflowEnroll(eid:UUID, flow:String,state: State, command: Command, ctx:ActorContext[Command]): Effect[State] = {
    startNewEnroll(eid,state,command)  
  }

  private def startNewEnroll(eid:UUID, state: State, command: Command): Effect[State] =
    command match {
      case Start(eid,flow,xid,replyTo) => 
        Effect
          .persist(state.addXid(xid))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case Continue(replyTo) =>
        log.info(s"${eid}: Continue...")
        replyTo ! StatusReply.Success(state.toSummary)
        return Effect.none

      case Finish(replyTo) =>
        if (state.isFinished) {
          replyTo ! StatusReply.Error(s"${eid}: Already finished")
          return Effect.none
        } 
        Effect
          .persist(state.finish(Instant.now()))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case UpdatePhase(phase, replyTo) =>
        Effect
          .persist(state.updatePhase(phase))
          //.thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case AddEmail(email, replyTo) =>
        if (email.isEmpty() || !email.contains('@')) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid email: '$email'")
          return Effect.none
        } 
        val token = Math.abs(Random.nextLong(100000000)).toString
        println(s"${eid}: Sending Confirmation email (token=${token}) -> ${email}...")

        Effect
          .persist(state.addEmail(email,token))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))
        
      case ConfirmEmail(token, replyTo) =>
        if (token.isEmpty() || Some(token) != state.confirmToken) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid Confirm token: '$token'")
          return Effect.none
        } 
        Effect
          .persist(state.confirmEmail())
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))
        
      case AddPublicKey(sig, replyTo) =>
        if (!sig.isValid()) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid Sig: '$sig'")
          return Effect.none
        }

        val data = generateSigData(eid,state.email.get)
        val pk = Eth.recoverMetamask(data,sig)

        if(pk.isFailure) {
          replyTo ! StatusReply.Error(s"${eid}: Signature: '$sig': ${pk}")
          return Effect.none
        }
        
        Effect
          .persist(state.addPublicKey(pk.get, sig))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case CreateUser(replyTo) =>
        // if(state.phase != "PK_ACK") {
        //   replyTo ! StatusReply.Error(s"${eid}: Invalid phase: ${state.phase}")
        //   return Effect.none
        // } 
        
        val user = UserService.create(state.email.get)
        if(!user.isDefined) {
          replyTo ! StatusReply.Error(s"${eid}: could not create user")
          return Effect.none
        } 

        Effect
          .persist(state.createUser(user.get.uid))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))
      
      case Get(replyTo) =>
        replyTo ! state.toSummary
        Effect.none

      case Info(replyTo) =>
        replyTo ! state
        Effect.none
    }

  private def finishedEnroll(eid:UUID, state: State, command: Command): Effect[State] =
    command match {
      case Get(replyTo) =>
        replyTo ! state.toSummary
        Effect.none
      case Info(replyTo) =>
        replyTo ! state
        Effect.none
      case cmd:Command =>
        print(s"${eid}: already finished: no more commands accepted")
        Effect.none
    }

  // private def handleEvent(state: State, event: Event) = {
  //   event match {
  //     case Started(_, xid) => state.addXid(xid)
  //     case EmailAdded(_, email,confirmToken) => state.addEmail(email,confirmToken)
  //     case EmailConfirmed(_) => state.confirmEmail()
  //     case PublicKeyAdded(_, pk, sig) => state.addPublicKey(pk,sig)
  //     case UserCreated(_, uid) => state.createUser(uid)
  //     case Finished(_, eventTime) => state.finish(eventTime)

  //     case PhaseUpdated(_, phase) => state.updatePhase(phase)
  //   }
  // }
}
