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
  final case class SearchUsers(search: String, from: Option[Long], size: Option[Long], replyTo: ActorRef[Users]) extends Command
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
        val fut = (from, size) match {
          case (Some(_), None) | (None, Some(_)) =>
            Future.failed(new IllegalArgumentException("from and size must both be set for paging"))
          case _ => store.list(from, size)
        }
        fut.foreach(p => replyTo ! Users(p.users, p.total))
        Behaviors.same

      case SearchUsers(search, from, size, replyTo) =>
        val fut = (from, size) match {
          case (Some(_), None) | (None, Some(_)) =>
            Future.failed(new IllegalArgumentException("from and size must both be set for paging"))
          case _ => store.search(search, from, size)
        }
        fut.foreach(p => replyTo ! Users(p.users, p.total))
        Behaviors.same

      case GetUser(id, replyTo) =>
        store.?(id).onComplete(replyTo ! _)
        Behaviors.same

      case GetUserByXid(eid, replyTo) =>
        store.findByXid(eid).onComplete {
          case Failure(e) =>
            log.warn(s"user not found: ${eid}: ${e.getMessage}")
            replyTo ! None
          case Success(opt) => replyTo ! opt
        }
        Behaviors.same

      case CreateUser(req, replyTo) =>
        val id = req.uid.getOrElse(UUID.randomUUID())
        val user = userFromCreateReq(id, req)

        store.?(id).onComplete {
          case Success(_) =>
            replyTo ! Failure(new Exception(s"already exists: ${id}"))
          case Failure(_) =>
            store.+(user).onComplete {
              case Failure(e) => replyTo ! Failure(e)
              case Success(_) => replyTo ! Success(user)
            }
        }

        Behaviors.same

      case UpdateUser(uid, req, replyTo) =>
        store.update(uid, req).onComplete(replyTo ! _)
        Behaviors.same

      case RandomUser(replyTo) =>
        Behaviors.same

      case DeleteUser(id, replyTo) =>
        store.del(id).onComplete {
          case Success(_) => replyTo ! UserActionRes("200", Some(id))
          case Failure(_) => replyTo ! UserActionRes("619", Some(id))
        }
        Behaviors.same
    }
  }
}
