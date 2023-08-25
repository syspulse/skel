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
import io.syspulse.skel.auth.permit.{Permitss, PermitsCreateReq, PermitsActionRes, PermitsUpdateReq}

object PermitsRegistry {    
  final case class CreatePermits(req: PermitsCreateReq, replyTo: ActorRef[Try[Permits]]) extends Command
  final case class GetPermitss(replyTo: ActorRef[Try[Permitss]]) extends Command
  final case class GetPermits(role:String, replyTo: ActorRef[Try[Permits]]) extends Command
  final case class DeletePermits(uid:UUID, replyTo: ActorRef[Try[PermitsActionRes]]) extends Command
  final case class UpdatePermits(uid:UUID,req:PermitsUpdateReq, replyTo: ActorRef[Try[Permits]]) extends Command

  final case class GetRoless(replyTo: ActorRef[Try[Roless]]) extends Command
  final case class CreateRoles(req: RolesCreateReq, replyTo: ActorRef[Try[Roles]]) extends Command
  final case class GetRoles(uid:UUID, replyTo: ActorRef[Try[Roles]]) extends Command
  final case class DeleteRoles(uid:UUID, replyTo: ActorRef[Try[PermitsActionRes]]) extends Command
  final case class UpdateRoles(uid:UUID,req:RolesUpdateReq, replyTo: ActorRef[Try[Roles]]) extends Command
  
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
        replyTo ! Success(Permitss(store.getPermits()))
        Behaviors.same

      case GetRoless(replyTo) =>
        replyTo ! Success(Roless(store.getRoles()))
        Behaviors.same

      case CreatePermits(req, replyTo) =>
        val p = Permits( req.role, req.permissions)              
        store.addPermits(p)        
        replyTo ! Success(p)
        Behaviors.same

      case CreateRoles(req, replyTo) =>
        val p = Roles(req.uid,req.roles)        
        store.addRoles(p)
        
        replyTo ! Success(p)
        Behaviors.same

      case GetPermits(role, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.getPermits(role)
          } yield c1
        }
        Behaviors.same

      case GetRoles(uid, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.getRoles(uid)
          } yield c1
        }
        Behaviors.same

      // case DeletePermits(uid, replyTo) =>
      //   replyTo ! {
      //     for {
      //       c1 <- store.?(uid)
      //       r <- {
      //         store.del(uid)
      //         Success(PermitsActionRes(s"deleted",Some(uid)))
      //       }
      //     } yield r
      //   }
      //   Behaviors.same

      // case UpdatePermits(uid, req, replyTo) =>
      //   replyTo ! store.update(uid,req.roles)
      //   Behaviors.same
    }
  }
}

