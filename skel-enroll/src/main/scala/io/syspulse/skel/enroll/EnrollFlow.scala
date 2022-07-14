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
import scala.util.Failure
import scala.util.Success

import io.syspulse.skel.enroll.event._

object EnrollFlow {
  val log = Logger(s"${this}")

  final case class StartFlow(eid:UUID,flow:String,xid:Option[String],replyTo: ActorRef[Command]) extends Command
  final case class FindFlow(eid:UUID,replyTo:ActorRef[Option[ActorRef[Command]]]) extends Command
  final case class ConfirmEmail(eid:UUID,code:String) extends Command
  final case class AddEmail(eid:UUID,email:String) extends Command
  final case class GetSummary(eid:UUID,replyTo:ActorRef[Option[Enroll.Summary]]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup(context => new EnrollFlow(context))

  class EnrollFlow(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {  
    var enrolls: Map[UUID,ActorRef[Command]] = Map()
    var listeners: Map[UUID,ActorRef[StatusReply[Enroll.Summary]]] = Map()

    log.info(s"EnrollFlow started")

    def enroll(eid:UUID,flow:String,xid:Option[String]): Behavior[Command] = Behaviors.setup { ctx =>
      Enroll(eid,flow) 
    }


    def enrollListener(enrollActor:ActorRef[Command],eid:UUID,flow:String,xid:Option[String]): Behavior[StatusReply[Enroll.Summary]] = Behaviors.setup { ctx =>
      
      def nextPhase(flow:String,phase:String, summary: Option[Enroll.Summary]=None):(String,Option[Command]) = {
        val phases = flow.split(",").map(_.toUpperCase())
        val next = phases.dropWhile(_ != phase).drop(1).headOption.getOrElse("")
        
        log.info(s"phase=${phase}, next=${next}")
        Option(phase) match {
          case Some("START") => (phase,Some(Enroll.Start(eid,flow,xid.getOrElse(""),ctx.self)))
          
          case Some("START_ACK") => nextPhase(flow,next,summary)
                  
          case Some("EMAIL") => //Some(Enroll.AddEmail("email@",ctx.self))
            log.info(s"Waiting for user email: email=${summary.get.email}")
            enrollActor ! Enroll.UpdatePhase("EMAIL",ctx.self)
            (phase,None)

          case Some("EMAIL_ACK") => nextPhase(flow,next,summary)
            
          case Some("CONFIRM_EMAIL") =>  //Some(Enroll.ConfirmEmail(token.get, ctx.self))
            log.info(s"Waiting for user to confirm email: ${summary.get.email}: token=${summary.get.confirmToken}")
            enrollActor ! Enroll.UpdatePhase("CONFIRM_EMAIL",ctx.self)
            (phase,None)

          case Some("CONFIRM_EMAIL_ACK") => nextPhase(flow,next,summary)

          case Some("CREATE_USER") => 
            (phase,Some(Enroll.CreateUser(ctx.self)))

          case Some("CREATE_USER_ACK") => nextPhase(flow ,next,summary)

          case Some("FINISH") =>
            log.info(s"Finishing: ${summary.get.eid}")
            (phase,Some(Enroll.Finish(ctx.self)))
            
          case Some("") =>
            log.warn(s"Finished: ${summary.get.eid}")
            (phase,None)

          case Some(s) => log.error(s"flow='${flow}' : found=${s} : phase not supported: ${phase}"); ("",None)

          case None => log.error(s"flow=${flow}: phase not found: ${phase}"); ("",None)
        }      
      }

      Behaviors.receiveMessage { msg =>
        log.info(s"<<< msg = ${msg}")

        msg match {
          case StatusReply.Success(s) => {                
            // WTF ?!
            val summary = s.asInstanceOf[Enroll.Summary]
            val phase0 = summary.phase
            log.info(s"phase0=${phase0}: summary=${s}")
            
            val (phase1,action) = nextPhase(flow,summary.phase,Some(summary))
            
            if(action.isDefined) { 
              log.info(s"phase1=${phase1}: action=${action} ---> ${enrollActor}")
              enrollActor ! action.get 
            }
            else
              log.info(s"phase1=${phase1}: waiting external action ...")
            Behaviors.same
          }

          case StatusReply.Error(e) => {
            log.info(s"error=${e}")
            Behaviors.same
          }
          case _ =>
            log.warn(s"UNKNOWN: ${msg}: push -> ${}")
            Behaviors.ignore
        }
      }  
    }


    override def onMessage(msg: Command): Behavior[Command] = {
      msg match {
        case FindFlow(eid,replyTo) =>
          replyTo ! enrolls.get(eid)
          this

        case GetSummary(eid,replyTo) => 
          val enrollActor = enrolls.get(eid)
            .getOrElse({
              val enrollActor = context.spawn(enroll(eid,"",None),s"Enroll-${eid}")
              val listener = context.spawn(enrollListener(enrollActor,eid,"",None),s"Listener-${eid}")
              enrolls = enrolls + (eid -> enrollActor)
              listeners = listeners + (eid -> listener)
              enrollActor
            })

          implicit val ec = context.executionContext
          
          enrollActor.ask{ ref =>  Enroll.Get(ref) }(Timeout(1 second),context.system.scheduler).onComplete( f => f match {
              case Failure(e) => replyTo ! None
              case Success(summary) => replyTo ! Some(summary)
          })
          
          this

        case StartFlow(eid,flow,xid,replyTo) =>
          val enrollActor = context.spawn(enroll(eid,flow,xid),s"Enroll-${eid}")
          val listener = context.spawn(enrollListener(enrollActor,eid,flow,xid),s"Listener-${eid}")
          enrolls = enrolls + (eid -> enrollActor)
          listeners = listeners + (eid -> listener)

          log.info(s"enrollActor=${enrollActor}")          
          enrollActor ! Enroll.Start(eid,flow,xid.getOrElse(""),listener)
          this

        case ConfirmEmail(eid,code) =>     
          val enrollActor = enrolls.get(eid)
          val listenerActor = listeners.get((eid))
          
          enrollActor.map(a => a ! Enroll.ConfirmEmail(code,listenerActor.get))
          this

        case AddEmail(eid,email) =>     
          val enrollActor = enrolls.get(eid)
          val listenerActor = listeners.get((eid))
          
          enrollActor.map(a => a ! Enroll.AddEmail(email,listenerActor.get))
          this

        case _ =>
          log.warn(s"UNKNOWN: ${msg}: ignoring")
          Behaviors.ignore
      }
    }
  }
    
}
