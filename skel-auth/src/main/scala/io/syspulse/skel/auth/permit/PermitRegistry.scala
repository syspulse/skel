package io.syspulse.skel.auth.permit

import scala.util.Failure
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._

import scala.util.Try
import scala.util.Success
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.Command
import io.syspulse.skel.auth.permit.PermitStoreMem
import io.syspulse.skel.auth.permit.{PermitRoles, PermitRoleCreateReq, PermitRoleActionRes, PermitRoleUpdateReq}
import io.syspulse.skel.auth.permit.{PermitUser, PermitRole}
import io.syspulse.skel.auth.permit.{PermitUserCreateReq, PermitUsers, PermitUserUpdateReq}
import io.syspulse.skel.auth.permit.PermitStore
import scala.concurrent.ExecutionContext

object PermitRegistry {
  final case class CreatePermitRole(req: PermitRoleCreateReq, replyTo: ActorRef[Try[PermitRole]]) extends Command
  final case class GetPermitRoles(replyTo: ActorRef[Try[PermitRoles]]) extends Command
  final case class GetPermitRole(role:String, replyTo: ActorRef[Try[PermitRole]]) extends Command
  final case class DeletePermitRole(uid:UUID, replyTo: ActorRef[Try[PermitRoleActionRes]]) extends Command
  final case class UpdatePermitRole(uid:UUID,req:PermitRoleUpdateReq, replyTo: ActorRef[Try[PermitRole]]) extends Command

  final case class GetPermitUsers(replyTo: ActorRef[Try[PermitUsers]]) extends Command
  final case class CreatePermitUser(req: PermitUserCreateReq, replyTo: ActorRef[Try[PermitUser]]) extends Command
  final case class GetPermitUser(uid:UUID, replyTo: ActorRef[Try[PermitUser]]) extends Command
  final case class GetPermitUserByXid(xid:String, replyTo: ActorRef[Try[PermitUser]]) extends Command
  final case class DeletePermitUser(uid:UUID, replyTo: ActorRef[Try[PermitUserActionRes]]) extends Command
  final case class UpdatePermitUser(uid:UUID,req:PermitUserUpdateReq, replyTo: ActorRef[Try[PermitUser]]) extends Command

  val log = Logger(s"${this}")

  // this var reference is unfortunately needed for Metrics access
  var store: PermitStore = new PermitStoreMem

  def apply(store: PermitStore): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: PermitStore): Behavior[Command] = {
    this.store = store

    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    Behaviors.receiveMessage {
      case GetPermitRoles(replyTo) =>
        store.getPermit().foreach(ps => replyTo ! Success(PermitRoles(ps)))
        Behaviors.same

      case GetPermitUsers(replyTo) =>
        store.getPermitUser().foreach(ps => replyTo ! Success(PermitUsers(ps)))
        Behaviors.same

      case CreatePermitRole(req, replyTo) =>
        log.info(s"role: ${req}")
        val p = PermitRole( req.role, req.resources)
        store.addPermit(p)
        replyTo ! Success(p)
        Behaviors.same

      case CreatePermitUser(req, replyTo) =>
        log.info(s"user: ${req}")
        val p = PermitUser(req.uid,req.roles,req.xid)
        store.addPermitUser(p)
        replyTo ! Success(p)
        Behaviors.same

      case GetPermitRole(role, replyTo) =>
        store.getPermit(role).onComplete(replyTo ! _)
        Behaviors.same

      case GetPermitUser(uid, replyTo) =>
        store.getPermitUser(uid).onComplete(replyTo ! _)
        Behaviors.same

      case GetPermitUserByXid(xid, replyTo) =>
        store.findPermitUserByXid(xid).onComplete(replyTo ! _)
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
