package io.syspulse.skel.user.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.Command

import io.syspulse.skel.user._
import io.syspulse.skel.user.server.{UserActionRes, Users, UserCreateReq, UserUpdateReq}

object UserRegistryAsync {
  val log = Logger(s"${this}")

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  import UserRegistryProto._

  def apply(store: UserStore): Behavior[io.syspulse.skel.Command] = {
    registry(store)
  }

  private def registry(store: UserStore): Behavior[io.syspulse.skel.Command] = {

    Behaviors.receiveMessage {
      case GetUsers(from, size, replyTo) =>
        val fut = (from, size) match {
          case (Some(f), Some(s)) => store.???(f, s)
          case (None, None)       => store.all
          case _ =>
            Future.failed(new IllegalArgumentException("from and size must both be set for paging"))
        }
        fut.foreach(r => replyTo ! Users(r))
        Behaviors.same

      case GetUser(id, replyTo) =>
        store.?(id).onComplete(replyTo ! _)
        Behaviors.same

      case GetUserByXid(eid, replyTo) =>
        store.findByXid(eid).onComplete(r =>
          r match {
            case Failure(e) =>
              log.warn(s"user not found: ${eid}")
              replyTo ! None
            case Success(opt) => replyTo ! opt
          },
        )
        Behaviors.same

      case CreateUser(req, replyTo) =>
        val id = req.uid.getOrElse(UUID.randomUUID())

        store.?(id).onComplete(_ match {
          case Success(_) =>
            replyTo ! Failure(new Exception(s"already exists: ${id}"))

          case _ =>
            val user = UserRegistry.userFromCreateReq(id, req)
            store.+(user).onComplete(r =>
              r match {
                case Failure(e) => replyTo ! Failure(e)
                case _          => replyTo ! Success(user)
              },
            )
        })

        Behaviors.same

      case UpdateUser(uid, req, replyTo) =>
        store.update(uid, req).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteUser(id, replyTo) =>
        store.del(id).onComplete(r =>
          r match {
            case Success(_) => replyTo ! UserActionRes("200", Some(id))
            case Failure(_) => replyTo ! UserActionRes("619", Some(id))
          },
        )

        Behaviors.same

      case RandomUser(_) =>
        Behaviors.same

      case TestTimeout(timeout, replyTo) =>
        scala.concurrent.Future {
          Thread.sleep(timeout)
          replyTo ! UserActionRes("300", None)
        }
        Behaviors.same
    }
  }
}
