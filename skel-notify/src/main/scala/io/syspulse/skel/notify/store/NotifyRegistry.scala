package io.syspulse.skel.notify.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.notify._
import scala.util.Try
import scala.util.Success
import scala.concurrent.ExecutionContext

object NotifyRegistry {
  val log = Logger(s"${this}")
  
  final case class GetNotifys(replyTo: ActorRef[Notifys]) extends Command
  final case class GetNotify(id:UUID,replyTo: ActorRef[Try[Notify]]) extends Command
  final case class GetNotifyUser(uid:UUID,fresh:Boolean,replyTo: ActorRef[Notifys]) extends Command
  final case class AckNotifyUser(uid:UUID,req:NotifyAckReq,replyTo: ActorRef[Try[Notify]]) extends Command
  final case class CreateNotify(uid:Option[UUID],req: NotifyReq, replyTo: ActorRef[Try[Notify]]) extends Command  
  
  // final case class DeleteNotify(id: UUID, replyTo: ActorRef[NotifyActionRes]) extends Command  
  
  // this var reference is unfortunately needed for Metrics access
  var store: NotifyStore = null

  def apply(store: NotifyStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: NotifyStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    Behaviors.receiveMessage {
      case GetNotifys(replyTo) =>
        store.all.foreach { all =>
          replyTo ! Notifys(all, Some(all.size.toLong))
        }
        Behaviors.same

      case GetNotify(id, replyTo) =>
        store.?(id).onComplete(replyTo ! _)
        Behaviors.same

      case GetNotifyUser(uid, fresh, replyTo) =>
        store.??(uid, fresh).foreach { nn =>
          replyTo ! Notifys(nn, Some(nn.size.toLong))
        }
        Behaviors.same

      case AckNotifyUser(uid, req, replyTo) =>
        store.ack(req.id).onComplete(replyTo ! _)
        Behaviors.same

      case CreateNotify(uid, req, replyTo) =>
        log.info(s"uid=${uid},${req}")        
        val n = Notify(
          req.to,
          req.subj, 
          req.msg, 
          System.currentTimeMillis(),
          severity = req.severity,
          scope = req.scope,
          uid = req.uid,
          from = if(req.from.isDefined) req.from else uid
        )
        
        val store1 = store.notify(n)

        replyTo ! Success(n)
        Behaviors.same
    }
  }
}
