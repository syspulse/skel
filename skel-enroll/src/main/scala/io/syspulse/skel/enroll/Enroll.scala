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


case class User(uid:UUID,email:String)

object UserService {

  def findByEmail(email:String):Option[User] = None

  def create(email:String):Option[User] = {
    Some(User(UUID.random,email))
  }
}

// case class EnrollSession(
//   id:UUID,
//   email:String,
//   phase:String = "START",
//   tsStart:Long = System.currentTimeMillis, 
//   tsPhase:Long = System.currentTimeMillis,
//   finished:Boolean = false,
//   data:Map[String,String] = Map()
// )

trait Command extends CborSerializable

object Enroll {

  final case class Summary(
    eid:UUID, 
    phase:String = "START", 
    email:Option[String] = None, addr:Option[String] = None, sig:Option[Signature] = None,
    tsStart:Long = 0L, tsPhase:Long = 0L, 
    finished: Boolean = false, 
    confirmToken:Option[String] = None) extends CborSerializable

  final case class State(eid:UUID, flow:Seq[String], phase:String = "START", 
    xid:Option[String] = None,
    email:Option[String] = None, 
    pk:Option[String] = None, sig:Option[String] = None,
    uid:Option[UUID] = None,
    tsStart:Long = System.currentTimeMillis, 
    tsPhase:Long = System.currentTimeMillis,
    finished:Boolean = false,
    confirmToken:Option[String] = None,
    data:Map[String,String] = Map()) extends CborSerializable {

    def isFinished: Boolean = finished

    def nextPhase(phase: String): State = copy(phase = phase)

    def addXid(xid:String): State = 
      copy(phase = "STARTED", xid = Some(xid),tsPhase=System.currentTimeMillis())
    def addEmail(email:String,token:String): State = 
      copy(phase = "CONFIRM_EMAIL", email = Some(email),tsPhase=System.currentTimeMillis(),confirmToken=Some(token))
    def confirmEmail(): State = 
      copy(phase = "EMAIL_CONFIRMED",tsPhase=System.currentTimeMillis(),confirmToken=None)
    def addPublicKey(pk:PK,sig:SignatureEth): State = 
      copy(phase = "PK_CONFIRMED", pk = Some(Util.hex(pk)), sig = Some(Util.hex(sig.toArray())), tsPhase=System.currentTimeMillis())
    def createUser(uid:UUID): State = 
      copy(phase = "USER_CREATED", uid=Some(uid), tsPhase=System.currentTimeMillis())
    
    def finish(now: Instant): State = 
      copy(phase = "FINISHED", tsPhase=now.getEpochSecond(), finished = true)

    def addData(k: String, v:String): State = copy(data = data + (k -> v))
    
    def toSummary: Summary = Summary(eid,phase, email, pk.map(Eth.address(_)), sig, tsStart,tsPhase,finished,confirmToken)
  }

  object State {
    def apply(eid:UUID,flow:String) = new State(eid,flow.split(",").map(_.trim.toUpperCase()))
  }


  final case class Start(eid:UUID,flow:String,xid:String,replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class AddEmail(email: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class ConfirmEmail(token: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class AddPublicKey(sig:SignatureEth, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class CreateUser(replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class Finish(replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command


  sealed trait Event extends CborSerializable {
    def eid:UUID
  }

  final case class Started(eid:UUID,xid:String) extends Event
  final case class EmailAdded(eid:UUID, email: String, confirmToken:String) extends Event
  final case class EmailConfirmed(eid:UUID) extends Event

  final case class PublicKeyAdded(eid:UUID, pk: PK, sig:SignatureEth) extends Event
  final case class UserCreated(eid:UUID, uid:UUID) extends Event

  //final case class ItemRemoved(cartId: String, itemId: String) extends Event
  //final case class ItemQuantityAdjusted(cartId: String, itemId: String, newQuantity: Int) extends Event

  final case class Finished(eid:UUID, eventTime: Instant) extends Event

  def apply(eid:UUID = UUID.random, flow:String = ""): Behavior[Command] =  Behaviors.setup { ctx => {
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
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }
  }

  def generateSigData(eid:UUID,email:String):String = {
    val tsSig = System.currentTimeMillis() / 5000L
    val data = s"${tsSig},${eid},${email}"
    data
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

      case AddEmail(email, replyTo) =>
        if (email.isEmpty() || !email.contains('@')) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid email: '$email'")
          return Effect.none
        } 
        val token = "123"//Random.nextInt(1000).toString
        println(s"${eid}: Sending Confirmation email (token=${token}) -> ${email}...")

        Effect
          .persist(EmailAdded(eid, email, token))
          .thenRun(updatedEnroll => replyTo ! StatusReply.Success(updatedEnroll.toSummary))
        
      case ConfirmEmail(token, replyTo) =>
        if (state.phase != "CONFIRM_EMAIL" || token.isEmpty() || Some(token) != state.confirmToken) {
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
        // if(state.phase != "PK_CONFIRMED") {
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
      case Started(_, xid) => {
        val state1 = state.addXid(xid)

        state1
      }
      case EmailAdded(_, email,confirmToken) => state.addEmail(email,confirmToken)
      case EmailConfirmed(_) => state.confirmEmail()
      case PublicKeyAdded(_, pk, sig) => state.addPublicKey(pk,sig)
      case UserCreated(_, uid) => state.createUser(uid)
      case Finished(_, eventTime) => state.finish(eventTime)
    }
  }
}
