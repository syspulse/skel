package io.syspulse.skel.enroll.flow.state

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
import akka.persistence.typed.state.{ RecoveryCompleted, RecoveryFailed}

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

import io.syspulse.skel.enroll.flow.Enrollment
import io.syspulse.skel.enroll.flow._
import io.syspulse.skel.user.UserService

object EnrollState extends Enrollment {
  import io.syspulse.skel.enroll.flow.Enrollment._
  
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
        case (state, recovery) => {
          recovery match {
            case RecoveryCompleted =>
              log.info(s"RECOVERY: ${eid}: ${state}")
              if(state.phase ==  "START") {
                // non-existant, invalid
                log.error(s"Enroll: ${eid}: Invalid (non-existant)")
              }
            case RecoveryFailed(e) => {
              log.error(s"Enroll: ${eid}: failed to recover",e)
            }
          }          
        }
        
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
          .thenRun(u => replyTo ! StatusReply.Success(u.toSummary))

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
          .thenRun(u => replyTo ! StatusReply.Success(u.toSummary))

      case UpdatePhase(phase, replyTo) =>
        Effect
          .persist(state.updatePhase(phase))
          //.thenRun(u => replyTo ! StatusReply.Success(u.toSummary))

      case AddEmail(email, replyTo) =>
        if (email.isEmpty() || !email.contains('@')) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid email: '$email'")
          return Effect.none
        } 
        val token = Math.abs(Random.nextLong(100000000)).toString
        log.info(s"${eid}: Confirmation email(${email}) token =  ${token})")

        Effect
          .persist(state.addEmail(email,token))
          .thenRun(u => replyTo ! StatusReply.Success(u.toSummary))
        
      case ConfirmEmail(token, replyTo) =>
        if (token.isEmpty() || Some(token) != state.confirmToken) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid Confirm token: '$token'")
          return Effect.none
        } 
        Effect
          .persist(state.confirmEmail())
          .thenRun(u => replyTo ! StatusReply.Success(u.toSummary))
        
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
          .thenRun(u => replyTo ! StatusReply.Success(u.toSummary))

      case CreateUser(replyTo) =>
        // if(state.phase != "PK_ACK") {
        //   replyTo ! StatusReply.Error(s"${eid}: Invalid phase: ${state.phase}")
        //   return Effect.none
        // } 
        
        val user = UserService.create(state.email.get,"",state.xid.getOrElse(""))
        log.info(s"user=${user}")
        
        if(!user.isDefined) {
          replyTo ! StatusReply.Error(s"${eid}: could not create user (${state.email},${state.xid})")
          return Effect.none
        } 

        Effect
          .persist(state.createUser(user.get.id))
          .thenRun(u => replyTo ! StatusReply.Success(u.toSummary))
      
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
        log.warn(s"${eid}: already finished: no more commands accepted")
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
