package io.syspulse.skel.auth.permit

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command
import scala.util.Try
import scala.util.Success

import io.syspulse.skel.auth.permit.PermitStoreMem

import scala.util.Failure
import io.syspulse.skel.auth.permit.{PermitRoles, PermitRoleCreateReq, PermitRoleActionRes, PermitRoleUpdateReq}
import io.syspulse.skel.auth.permit.{PermitUser, PermitRole}
import io.syspulse.skel.auth.permit.{PermitUserCreateReq, PermitUsers, PermitUserUpdateReq}
import io.syspulse.skel.auth.permit.PermitStore

object PermitRegistry {    
  final case class CreatePermitRole(req: PermitRoleCreateReq, replyTo: ActorRef[Try[PermitRole]]) extends Command
  final case class GetPermitRoles(replyTo: ActorRef[Try[PermitRoles]]) extends Command
  final case class GetPermitRole(role:String, replyTo: ActorRef[Try[PermitRole]]) extends Command
  final case class DeletePermitRole(uid:UUID, replyTo: ActorRef[Try[PermitRoleActionRes]]) extends Command
  final case class UpdatePermitRole(uid:UUID,req:PermitRoleUpdateReq, replyTo: ActorRef[Try[PermitRole]]) extends Command

  final case class GetPermitUsers(replyTo: ActorRef[Try[PermitUsers]]) extends Command
  final case class CreatePermitUser(req: PermitUserCreateReq, replyTo: ActorRef[Try[PermitUser]]) extends Command
  final case class GetPermitUser(uid:UUID, replyTo: ActorRef[Try[PermitUser]]) extends Command
  final case class DeletePermitUser(uid:UUID, replyTo: ActorRef[Try[PermitUserActionRes]]) extends Command
  final case class UpdatePermitUser(uid:UUID,req:PermitUserUpdateReq, replyTo: ActorRef[Try[PermitUser]]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: PermitStore = new PermitStoreMem

  def apply(store: PermitStore): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: PermitStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetPermitRoles(replyTo) =>
        replyTo ! Success(PermitRoles(store.getPermit()))
        Behaviors.same

      case GetPermitUsers(replyTo) =>
        replyTo ! Success(PermitUsers(store.getPermitUser()))
        Behaviors.same

      case CreatePermitRole(req, replyTo) =>
        val p = PermitRole( req.role, req.resources)              
        store.addPermit(p)        
        replyTo ! Success(p)
        Behaviors.same

      case CreatePermitUser(req, replyTo) =>
        val p = PermitUser(req.uid,req.roles)        
        store.addPermitUser(p)
        
        replyTo ! Success(p)
        Behaviors.same

      case GetPermitRole(role, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.getPermit(role)
          } yield c1
        }
        Behaviors.same

      case GetPermitUser(uid, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.getPermitUser(uid)
          } yield c1
        }
        Behaviors.same

      // case DeletePermit(uid, replyTo) =>
      //   replyTo ! {
      //     for {
      //       c1 <- store.?(uid)
      //       r <- {
      //         store.del(uid)
      //         Success(PermitActionRes(s"deleted",Some(uid)))
      //       }
      //     } yield r
      //   }
      //   Behaviors.same

      // case UpdatePermit(uid, req, replyTo) =>
      //   replyTo ! store.update(uid,req.roles)
      //   Behaviors.same
    }
  }
}

