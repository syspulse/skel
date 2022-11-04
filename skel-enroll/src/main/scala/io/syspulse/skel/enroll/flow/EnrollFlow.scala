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
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect

import io.jvm.uuid._

import io.syspulse.skel.crypto.key.{PK,Signature}
import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.SignatureEth
import akka.util.Timeout
import scala.concurrent.Await

import io.syspulse.skel.enroll.flow.Command
import scala.util.Failure
import scala.util.Success

import io.syspulse.skel.enroll.flow._
import io.syspulse.skel.enroll.flow.phase._

object EnrollFlow {
  val log = Logger(s"${this}")

  import io.syspulse.skel.enroll.flow.Enrollment._

  final case class StartFlow(eid:UUID,enrollType:String,flow:String,xid:Option[String],replyTo: ActorRef[Command]) extends Command
  final case class ContinueFlow(eid:UUID) extends Command
  final case class FindFlow(eid:UUID,replyTo:ActorRef[Option[ActorRef[Command]]]) extends Command
  final case class ConfirmEmail(eid:UUID,code:String) extends Command
  final case class AddEmail(eid:UUID,email:String) extends Command
  final case class GetSummary(eid:UUID,enrollType:String,replyTo:ActorRef[Option[Enrollment.Summary]]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup(context => new EnrollFlow(context))

  class EnrollFlow(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {  
    var enrolls: Map[UUID,ActorRef[Command]] = Map()
    var listeners: Map[UUID,ActorRef[StatusReply[Enrollment.Summary]]] = Map()

    log.info(s"EnrollFlow started")

    def enroll(eid:UUID,enrollType:String,flow:String,xid:Option[String]): Behavior[Command] = Behaviors.setup { ctx =>
      // temporary solution, not type to build Class heirarchy
      enrollType.toLowerCase() match {
        case "event" => event.EnrollEvent(eid,flow)
        case "state" | "" => state.EnrollState(eid,flow)
      }
    }

    def enrollListener(enrollActor:ActorRef[Command],eidStart:UUID,flowStart:String,xid:Option[String]): Behavior[StatusReply[Enrollment.Summary]] = Behaviors.setup { ctx =>
      val timeout = Timeout(1 second)
      
      // get recoverted flow state 
      val f = enrollActor.ask{ ref =>  Enrollment.Info(ref) }(timeout,ctx.system.scheduler)
      val r = Await.result(f,timeout.duration)
      val flow = if(flowStart.isEmpty()) r.flow.mkString(",") else flowStart
      val eid = eidStart

      def nextPhase(flow:String, phase:String, summary: Option[Enrollment.Summary]=None):(String,Option[Command]) = {
        val phases = flow.split(",").map(_.toUpperCase())
        val next = phases.dropWhile(_ != phase).drop(1).headOption.getOrElse("")
        
        log.info(s"phase=${phase}, next=${next}")
        Option(phase) match {
          case Some("START") => (phase,Some(Enrollment.Start(eid,flow,xid.getOrElse(""),ctx.self)))
          
          case Some("START_ACK") => nextPhase(flow,next,summary)
                  
          case Some("EMAIL") =>             
            enrollActor ! Enrollment.UpdatePhase("EMAIL",ctx.self)
            (phase,None)

          case Some("EMAIL_ACK") => 
            val email = summary.get.email.getOrElse("")
            val code = summary.get.confirmToken.getOrElse("")
            log.info(s"Sending email -> user (email=${email})")
            Phases.get("EMAIL_ACK").map( phase => phase.run(Map("email" -> email, "code" -> code)))
            
            nextPhase(flow,next,summary)
            
          case Some("CONFIRM_EMAIL") => 
            log.info(s"Waiting for user to confirm email: ${summary.get.email}: token=${summary.get.confirmToken}")
            enrollActor ! Enrollment.UpdatePhase("CONFIRM_EMAIL",ctx.self)
            (phase,None)

          case Some("CONFIRM_EMAIL_ACK") => nextPhase(flow,next,summary)

          case Some("CREATE_USER") => 
            val email = summary.get.email.getOrElse("")
            val name = summary.get.name.getOrElse("")
            val xid = summary.get.xid.getOrElse("")
            val avatar = summary.get.avatar.getOrElse("")
            
            val r = Phases.get("CREATE_USER").map( phase => phase.run(Map("email" -> email, "name" -> name, "xid" -> xid, "avatar" -> avatar)))
            
            r.get match {
              case Success(uid) => (phase,Some(Enrollment.CreateUser(UUID(uid),ctx.self)))
              case Failure(f) => (phase,None)
            }            

          case Some("CREATE_USER_ACK") => nextPhase(flow ,next,summary)

          case Some("FINISH") =>
            log.info(s"Finishing: ${summary.get.eid}")
            (phase,Some(Enrollment.Finish(ctx.self)))
            
          case Some("FINISH_ACK") =>
            log.info(s"Finished: ${summary.get.eid}")

            val uid = summary.get.uid.getOrElse("")
            val email = summary.get.email.getOrElse("")
            val name = summary.get.name.getOrElse("")
            
            Phases.get("FINISH_ACK").map( phase => phase.run(Map("uid" -> uid, "email" -> email, "name"->name)))
            
            (phase,None)

          case Some("") =>
            log.info(s"Finished: ${summary.get.eid}")                  
            (phase,None)

          case Some(s) => log.error(s"flow='${flow}' : found=${s} : phase not supported: ${phase}"); ("",None)

          case None => log.error(s"flow=${flow}: phase not found: ${phase}"); ("",None)
        }      
      }

      Behaviors.receiveMessage { msg =>
        log.info(s"<= msg(${msg})")

        msg match {
          case StatusReply.Success(s) => {                
            // WTF ?!
            val summary = s.asInstanceOf[Enrollment.Summary]
            val phase0 = summary.phase
            log.info(s"${summary.eid}: phase0=${phase0}: summary=${s}")
            
            val (phase1,action) = nextPhase(flow,summary.phase,Some(summary))
            
            if(action.isDefined) { 
              log.info(s"${summary.eid}: phase1=${phase1}: action=${action} ---> ${enrollActor}")
              enrollActor ! action.get 
            }
            else
              log.info(s"${summary.eid}: phase1=${phase1}: waiting external action ...")
            Behaviors.same
          }

          case StatusReply.Error(e) => {
            log.error(s"error=${e.getMessage()}")
            Behaviors.same
          }
          case _ =>
            log.warn(s"UNKNOWN: ${msg}: push -> ${}")
            Behaviors.ignore
        }
      }  
    }

    def find(eid:UUID,enrollType:String = "state") = {
      val enrollActor = enrolls.get(eid)
        .getOrElse({
          val enrollActor = context.spawn(enroll(eid,enrollType,"",None),s"Enroll-${eid}")
          val listener = context.spawn(enrollListener(enrollActor,eid,"",None),s"Listener-${eid}")
          enrolls = enrolls + (eid -> enrollActor)
          listeners = listeners + (eid -> listener)
          enrollActor
        })
      enrollActor
    }

    override def onMessage(msg: Command): Behavior[Command] = {
      log.info(s"<- command: ${msg}")

      msg match {
        case FindFlow(eid,replyTo) =>
          replyTo ! enrolls.get(eid)
          this

        case GetSummary(eid,enrollType,replyTo) =>
          val enrollActor = find(eid,enrollType)
          implicit val ec = context.executionContext
          
          enrollActor.ask{ ref =>  Enrollment.Get(ref) }(Timeout(1 second),context.system.scheduler).onComplete( f => f match {
              case Failure(e) => replyTo ! None
              case Success(summary) => replyTo ! Some(summary)
          })
          
          this

        case StartFlow(eid,enrollType,flow,xid,replyTo) =>
          val enrollActor = context.spawn(enroll(eid,enrollType,flow,xid),s"Enroll-${eid}")
          val listener = context.spawn(enrollListener(enrollActor,eid,flow,xid),s"Listener-${eid}")
          enrolls = enrolls + (eid -> enrollActor)
          listeners = listeners + (eid -> listener)

          log.info(s"enrollActor=${enrollActor}")          
          enrollActor ! Enrollment.Start(eid,flow,xid.getOrElse(""),listener)
          this

        case ContinueFlow(eid) =>
          val enrollActor = find(eid)
          val listenerActor = listeners.get((eid))          
          enrollActor ! Enrollment.Continue(listenerActor.get)
          this

        case AddEmail(eid,email) =>     
          val enrollActor = find(eid)
          val listenerActor = listeners.get((eid))
          
          enrollActor ! Enrollment.AddEmail(email,listenerActor.get)
          this

        case ConfirmEmail(eid,code) =>     
          val enrollActor = find(eid)
          val listenerActor = listeners.get((eid))
          
          enrollActor ! Enrollment.ConfirmEmail(code,listenerActor.get)
          this

        case _ =>
          log.warn(s"UNKNOWN: ${msg}: ignoring")
          Behaviors.ignore
      }
    }
  }
    
}
