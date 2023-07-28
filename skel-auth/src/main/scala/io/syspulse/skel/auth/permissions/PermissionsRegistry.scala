package io.syspulse.skel.auth.permissions

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command
import scala.util.Try
import scala.util.Success

import io.syspulse.skel.auth.permissions.PermissionsStoreMem

import scala.util.Failure

object PermissionsRegistry {    
  final case class CreatePermissions(req: PermissionsCreateReq, replyTo: ActorRef[Try[Permissions]]) extends Command
  final case class GetPermissionss(replyTo: ActorRef[Try[Permissionss]]) extends Command
  final case class GetPermissions(uid:UUID, replyTo: ActorRef[Try[Permissions]]) extends Command
  final case class DeletePermissions(uid:UUID, replyTo: ActorRef[Try[PermissionsActionRes]]) extends Command
  final case class UpdatePermissions(uid:UUID,req:PermissionsUpdateReq, replyTo: ActorRef[Try[Permissions]]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: PermissionsStore = new PermissionsStoreMem

  def apply(store: PermissionsStore): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: PermissionsStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetPermissionss(replyTo) =>
        replyTo ! Success(Permissionss(store.all))
        Behaviors.same

      case CreatePermissions(req, replyTo) =>
        val p = Permissions(
          req.uid,
          req.permissions
        )
        
        store.+(p)
        
        replyTo ! Success(p)
        Behaviors.same

      case GetPermissions(uid, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.?(uid)
          } yield c1
        }
        Behaviors.same

      case DeletePermissions(uid, replyTo) =>
        replyTo ! {
          for {
            c1 <- store.?(uid)
            r <- {
              store.del(uid)
              Success(PermissionsActionRes(s"deleted",Some(uid)))
            }
          } yield r
        }
        Behaviors.same

      case UpdatePermissions(uid, req, replyTo) =>
        replyTo ! store.update(uid,req.permissions)
        Behaviors.same
    }
  }
}

