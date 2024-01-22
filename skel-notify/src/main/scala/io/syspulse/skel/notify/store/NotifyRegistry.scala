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

    Behaviors.receiveMessage {
      case GetNotifys(replyTo) =>
        replyTo ! Notifys(store.all,Some(store.size))
        Behaviors.same

      case GetNotify(id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case GetNotifyUser(uid, fresh, replyTo) =>
        val nn = store.??(uid,fresh)
        replyTo ! Notifys(nn,Some(nn.size)) 
        Behaviors.same

      case AckNotifyUser(uid, req, replyTo) =>
        val n = store.ack(req.id)
        replyTo ! n
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
