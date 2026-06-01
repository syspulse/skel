package io.syspulse.skel.user.store

import scala.util.{Try, Success, Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.Command

import io.syspulse.skel.user._
import io.syspulse.skel.user.server.{UserActionRes, Users, UserCreateReq, UserUpdateReq}
import scala.concurrent.Future
import scala.concurrent.ExecutionContextExecutor
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object UserRegistryProto {
  final case class GetUsers(from: Option[Long], size: Option[Long], replyTo: ActorRef[Users]) extends Command
  final case class GetUser(id: UUID, replyTo: ActorRef[Try[User]]) extends Command
  final case class GetUserByXid(xid: String, replyTo: ActorRef[Option[User]]) extends Command

  final case class CreateUser(req: UserCreateReq, replyTo: ActorRef[Try[User]]) extends Command
  final case class UpdateUser(uid: UUID, req: UserUpdateReq, replyTo: ActorRef[Try[User]]) extends Command
  final case class RandomUser(replyTo: ActorRef[User]) extends Command

  final case class DeleteUser(id: UUID, replyTo: ActorRef[UserActionRes]) extends Command

  final case class TestTimeout(timeout: Long, replyTo: ActorRef[UserActionRes]) extends Command
}

object UserRegistry {
  val log = Logger(s"${this}")

  import UserRegistryProto._

  var store: UserStore = null

  implicit val ec: ExecutionContextExecutor = {
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))
  }

  def userFromCreateReq(id: UUID, req: UserCreateReq): User = {
    val now = System.currentTimeMillis()
    User(
      id = id,
      email = req.email.trim.toLowerCase,
      name = req.name.filter(_.nonEmpty),
      xid = req.xid.filter(_.nonEmpty),
      avatar = req.avatar.filter(_.nonEmpty),
      ts0 = now,
      ts = now,
      meta = req.meta,
    )
  }

  def apply(store: UserStore = new UserStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: UserStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case TestTimeout(timeout, replyTo) =>
        Future {
          log.info(s"TestTimeout: ${timeout} <-")
          Thread.sleep(timeout)
          log.info(s"TestTimeout: ${timeout} ->")
          replyTo ! UserActionRes("300", None)
        }
        Behaviors.same

      case GetUsers(from, size, replyTo) =>
        val users = (from, size) match {
          case (Some(f), Some(s)) => store.??(f, s)
          case (None, None)       => store.all
          case _ =>
            throw new IllegalArgumentException("from and size must both be set for paging")
        }
        replyTo ! Users(users)
        Behaviors.same

      case GetUser(id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case GetUserByXid(eid, replyTo) =>
        replyTo ! store.findByXid(eid)
        Behaviors.same

      case CreateUser(req, replyTo) =>
        val id = req.uid.getOrElse(UUID.randomUUID())

        store.?(id) match {
          case Success(_) =>
            replyTo ! Failure(new Exception(s"already exists: ${id}"))
          case _ =>
            val user = userFromCreateReq(id, req)
            replyTo ! store.+(user).map(_ => user)
        }

        Behaviors.same

      case UpdateUser(uid, req, replyTo) =>
        replyTo ! store.update(uid, req)
        Behaviors.same

      case RandomUser(replyTo) =>
        Behaviors.same

      case DeleteUser(id, replyTo) =>
        val r = store.del(id)
        r match {
          case Success(_) => replyTo ! UserActionRes("200", Some(id))
          case Failure(_) => replyTo ! UserActionRes("619", Some(id))
        }
        Behaviors.same
    }
  }
}
