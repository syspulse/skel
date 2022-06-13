package io.syspulse.skel.enroll

import java.time.Instant
import scala.util.Random
import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior

import io.jvm.uuid._

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

object Enroll {

  final case class Summary(eid:UUID, email:Option[String], phase:String, tsStart:Long, tsPhase:Long, finished: Boolean, confirmToken:Option[String]) extends CborSerializable

  final case class State(eid:UUID, email:Option[String] = None, phase:String = "START",
    uid:Option[UUID] = None,
    tsStart:Long = System.currentTimeMillis, 
    tsPhase:Long = System.currentTimeMillis,
    finished:Boolean = false,
    confirmToken:Option[String] = None,
    data:Map[String,String] = Map()) extends CborSerializable {

    def isFinished: Boolean = finished

    def nextPhase(phase: String): State = copy(phase = phase)

    def addEmail(email:String,token:String): State = 
      copy(email = Some(email),phase = "CONFIRM_EMAIL",tsPhase=System.currentTimeMillis(),confirmToken=Some(token))
    def confirmEmail(): State = 
      copy(phase = "EMAIL_CONFIRMED",tsPhase=System.currentTimeMillis(),confirmToken=None)
    def createUser(uid:UUID): State = 
      copy(phase = "USER_CREATED",tsPhase=System.currentTimeMillis(),uid=Some(uid))
    
    def finish(now: Instant): State = copy(finished = true)

    def addData(k: String, v:String): State = copy(data = data + (k -> v))
    
    def toSummary: Summary = Summary(eid,email,phase,tsStart,tsPhase,finished,confirmToken)
  }

  object State {
    def apply(eid:UUID) = new State(eid)
  }

  sealed trait Command extends CborSerializable

  final case class AddEmail(email: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class ConfirmEmail(token: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  final case class CreateUser(replyTo: ActorRef[StatusReply[Summary]]) extends Command

  // final case class RemoveItem(itemId: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command
  // final case class AdjustItemQuantity(itemId: String, quantity: Int, replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Finish(replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command


  sealed trait Event extends CborSerializable {
    def eid:UUID
  }

  final case class EmailAdded(eid:UUID, email: String, confirmToken:String) extends Event
  final case class EmailConfirmed(eid:UUID) extends Event
  final case class UserCreated(eid:UUID, uid:UUID) extends Event

  //final case class ItemRemoved(cartId: String, itemId: String) extends Event
  //final case class ItemQuantityAdjusted(cartId: String, itemId: String, newQuantity: Int) extends Event

  final case class Finished(eid:UUID, eventTime: Instant) extends Event

  def apply(eid:UUID = UUID.random): Behavior[Command] = {
    EventSourcedBehavior[Command, Event, State](
      PersistenceId("Enroll", eid.toString()),
      State(eid),
      (state, command) =>
        if (state.isFinished) 
          finishedEnroll(eid, state, command)
        else 
          startEnroll(eid, state, command),
      (state, event) => handleEvent(state, event))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def startEnroll(eid:UUID, state: State, command: Command): Effect[Event, State] =
    command match {
      case AddEmail(email, replyTo) =>
        if (email.isEmpty() || !email.contains('@')) {
          replyTo ! StatusReply.Error(s"${eid}: Invalid email: '$email'")
          return Effect.none
        } 
        val token = Random.nextInt(1000).toString
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
        
      case CreateUser(replyTo) =>
        if (state.phase != "EMAIL_CONFIRMED") {
          replyTo ! StatusReply.Error(s"${eid}: Invalid phase: ${state.phase}")
          return Effect.none
        } 
        
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
      case EmailAdded(_, email,confirmToken) => state.addEmail(email,confirmToken)
      case EmailConfirmed(_) => state.confirmEmail()
      case UserCreated(_, uid) => state.createUser(uid)
      case Finished(_, eventTime) => state.finish(eventTime)
    }
  }
}
