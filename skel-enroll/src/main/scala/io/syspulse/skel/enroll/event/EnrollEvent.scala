package io.syspulse.skel.enroll.event

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
import akka.persistence.typed.RecoveryCompleted

import io.syspulse.skel.enroll.Enroll
import io.syspulse.skel.enroll._

object EnrollEvent extends Enroll {
  import io.syspulse.skel.enroll.Enroll._

  sealed trait Event extends CborSerializable {
    def eid:UUID
  }

  final case class Started(eid:UUID,xid:String) extends Event
  final case class EmailAdded(eid:UUID, email: String, confirmToken:String) extends Event
  final case class EmailConfirmed(eid:UUID) extends Event
  final case class PublicKeyAdded(eid:UUID, pk: PK, sig:SignatureEth) extends Event
  final case class UserCreated(eid:UUID, uid:UUID) extends Event
  final case class Finished(eid:UUID, eventTime: Instant) extends Event
  final case class PhaseUpdated(eid:UUID, phase: String) extends Event

  override def apply(eid:UUID = UUID.random, flow:String = ""): Behavior[Command] =  Behaviors.setup { ctx => {
    EventSourcedBehavior[Command, Event, State](
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
      },
      eventHandler = (state, event) => handleEvent(state, event))
      .receiveSignal {
        case (state, RecoveryCompleted) =>
          log.info(s"RECOVERY: ${eid}: =========> ${state}")
      }
      // .snapshotWhen((state, _, _) => {
      //   log.info(s"SNAPSHOT: ${ctx.self.path.name} => state: ${state}")
      //   true
      // })
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }
  }

  private def startAutoflowEnroll(eid:UUID, flow:String,state: State, command: Command, ctx:ActorContext[Command]): Effect[Event, State] = {
    startNewEnroll(eid,state,command)  
  }

  private def startNewEnroll(eid:UUID, state: State, command: Command): Effect[Event, State] =
    command match {
      case Start(eid,flow,xid,replyTo) => 
        Effect
          .persist(Started(eid,xid))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case Finish(replyTo) => 
        Effect
          .persist(Finished(eid,Instant.now()))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case UpdatePhase(phase, replyTo) =>
        Effect
          .persist(PhaseUpdated(eid, phase))
          //.thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case AddEmail(email, replyTo) =>
        if (email.isEmpty() || !email.contains('@')) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid email: '$email'")
          return Effect.none
        } 
        val token = Math.abs(Random.nextLong(100000000)).toString
        println(s"${eid}: Sending Confirmation email (token=${token}) -> ${email}...")

        Effect
          .persist(EmailAdded(eid, email, token))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))
        
      case ConfirmEmail(token, replyTo) =>
        if (token.isEmpty() || Some(token) != state.confirmToken) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid Confirm token: '$token'")
          return Effect.none
        } 
        Effect
          .persist(EmailConfirmed(eid))
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
          .persist(PublicKeyAdded(eid, pk.get, sig))
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
          .persist(UserCreated(eid,user.get.uid))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))

      case Finish(replyTo) =>
        if (state.isFinished) {
          replyTo ! StatusReply.Error(s"${eid}: Already finished")
          return Effect.none
        } 
        Effect
          .persist(Finished(eid, Instant.now()))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))
      
      case Get(replyTo) =>
        replyTo ! state.toSummary
        Effect.none
    }

  private def finishedEnroll(eid:UUID, state: State, command: Command): Effect[Event, State] =
    command match {
      case Get(replyTo) =>
        replyTo ! state.toSummary
        Effect.none
      case cmd:Command =>
        print(s"${eid}: already finished")
        Effect.none
    }

  private def handleEvent(state: State, event: Event) = {
    event match {
      case Started(_, xid) => state.addXid(xid)
      case EmailAdded(_, email,confirmToken) => state.addEmail(email,confirmToken)
      case EmailConfirmed(_) => state.confirmEmail()
      case PublicKeyAdded(_, pk, sig) => state.addPublicKey(pk,sig)
      case UserCreated(_, uid) => state.createUser(uid)
      case Finished(_, eventTime) => state.finish(eventTime)

      case PhaseUpdated(_, phase) => state.updatePhase(phase)
    }
  }
}