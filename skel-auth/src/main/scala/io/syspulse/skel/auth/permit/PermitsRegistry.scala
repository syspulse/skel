package io.syspulse.skel.auth.permit

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command
import scala.util.Try
import scala.util.Success

import io.syspulse.skel.auth.permit.PermitsStoreMem

import scala.util.Failure

object PermitsRegistry {    
  final case class CreatePermits(req: PermitsCreateReq, replyTo: ActorRef[Try[Permits]]) extends Command
  final case class GetPermitss(replyTo: ActorRef[Try[Permitss]]) extends Command
  final case class GetPermits(uid:UUID, replyTo: ActorRef[Try[Permits]]) extends Command
  final case class DeletePermits(uid:UUID, replyTo: ActorRef[Try[PermitsActionRes]]) extends Command
  final case class UpdatePermits(uid:UUID,req:PermitsUpdateReq, replyTo: ActorRef[Try[Permits]]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: PermitsStore = new PermitsStoreMem

  def apply(store: PermitsStore): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: PermitsStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetPermitss(replyTo) =>
        replyTo ! Success(Permitss(store.all))
        Behaviors.same

      case CreatePermits(req, replyTo) =>
        val p = Permits(
          req.uid,
          req.Permits,
          req.roles
        )
        
        store.+(p)
        
        replyTo ! Success(p)
        Behaviors.same

      case GetPermits(uid, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.?(uid)
          } yield c1
        }
        Behaviors.same

      case DeletePermits(uid, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.?(uid)
            r <- {
              store.del(uid)
              Success(PermitsActionRes(s"deleted",Some(uid)))
            }
          } yield r
        }
        Behaviors.same

      case UpdatePermits(uid, req, replyTo) =>
        replyTo ! store.update(uid,req.Permits)
        Behaviors.same
    }
  }
}

