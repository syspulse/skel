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

object EnrollManager {
  val log = Logger(s"${this}")

  final case class StartFlow(eid:UUID,flow:String,xid:Option[String],replyTo: ActorRef[Command]) extends Command
  final case class FindFlow(eid:UUID,replyTo:ActorRef[Option[ActorRef[Command]]]) extends Command
  final case class ConfirmEmailFlow(eid:UUID,code:String) extends Command

  def apply(): Behavior[Command] = Behaviors.setup(context => new EnrollManager(context))

  class EnrollManager(context: ActorContext[Command]) extends AbstractBehavior[Command](context) {  
    var enrolls: Map[UUID,ActorRef[Command]] = Map()
    var listeners: Map[UUID,ActorRef[StatusReply[Enroll.Summary]]] = Map()

    log.info(s"EnrollManager started")

    def enroll(eid:UUID,flow:String,xid:Option[String]): Behavior[Command] = Behaviors.setup { ctx =>
      Enroll(eid,flow) 
    }


    def enrollListener(enrollActor:ActorRef[Command],eid:UUID,flow:String,xid:Option[String]): Behavior[StatusReply[Enroll.Summary]] = Behaviors.setup { ctx =>
      
      def nextPhase(flow:String,phase:String, summary: Option[Enroll.Summary]=None):Option[Command] = {
        val phases = flow.split(",").map(_.toUpperCase())
        val next = phases.dropWhile(_ != phase).drop(1).headOption.getOrElse("")
        
        log.info(s"phase=${phase}, next=${next}")
        Option(phase) match {
          case Some("START") => Some(Enroll.Start(eid,flow,xid.getOrElse(""),ctx.self))
          
          case Some("STARTED") => nextPhase(flow,next,summary)
                  
          case Some("EMAIL") => Some(Enroll.AddEmail("email@",ctx.self))
          
          case Some("CONFIRM_EMAIL") =>  //Some(Enroll.ConfirmEmail(token.get, ctx.self))
            log.info(s"Waiting for user to confirm email: ${summary.get.confirmToken}")
            None

          case Some("EMAIL_CONFIRMED") => nextPhase(flow,next,summary)

          case Some("CREATE_USER") => Some(Enroll.CreateUser(ctx.self))

          case Some("USER_CREATED") => nextPhase(flow ,next,summary)

          case Some("FINISH") =>
            log.info(s"Finishing: ${summary.get.eid}")
            Some(Enroll.Finish(ctx.self))
            
          case Some("") =>
            log.warn(s"Finished: ${summary.get.eid}")
            None

          case Some(s) => log.error(s"flow='${flow}' : found=${s} : phase not supported: ${phase}"); None

          case None => log.error(s"flow=${flow}: phase not found: ${phase}"); None
        }      
      }

      Behaviors.receiveMessage { msg =>
        log.info(s">>> msg = ${msg}")

        msg match {
          case StatusReply.Success(s) => {                
            // WTF ?!
            val summary = s.asInstanceOf[Enroll.Summary]
            log.info(s"phase=${summary.phase}: summary=${s}")
            
            val action = nextPhase(flow,summary.phase,Some(summary))
            log.info(s"phase=${summary.phase}: action=${action}")

            if(action.isDefined)
              enrollActor ! action.get 
            else
              log.info(s"phase=${summary.phase}: action=${action}: ...")
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

        case StartFlow(eid,flow,xid,replyTo) =>
          val enrollActor = context.spawn(enroll(eid,flow,xid),s"Enroll-${eid}")
          val listener = context.spawn(enrollListener(enrollActor,eid,flow,xid),s"Listener-${eid}")
          enrolls = enrolls + (eid -> enrollActor)
          listeners = listeners + (eid -> listener)

          log.info(s"enrollActor=${enrollActor}")          
          enrollActor ! Enroll.Start(eid,flow,xid.getOrElse(""),listener)
          this

        case ConfirmEmailFlow(eid,code) =>     
          val enrollActor = enrolls.get(eid)
          val listenerActor = listeners.get((eid))
          
          //log.info(s"${enrollActor} ${listenerActor}")
          enrollActor.map(a => a ! Enroll.ConfirmEmail(code,listenerActor.get))

          this

        case _ =>
          log.warn(s"UNKNOWN: ${msg}: ignoring")
          Behaviors.ignore
      }
    }
  }
    
}
