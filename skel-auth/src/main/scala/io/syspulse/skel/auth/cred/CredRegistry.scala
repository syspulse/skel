package io.syspulse.skel.auth.cred

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command
import scala.util.Try
import scala.util.Success

import io.syspulse.skel.auth.cred.CredStoreMem
import scala.util.Failure
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object CredRegistry {
  final case class CreateCred(req: CredCreateReq, uid:UUID, replyTo: ActorRef[Try[Cred]]) extends Command
  final case class GetCred(cid: String, uid:Option[UUID], replyTo: ActorRef[Try[Cred]]) extends Command
  final case class GetCreds(uid:Option[UUID], replyTo: ActorRef[Try[Creds]]) extends Command
  final case class DeleteCred(cid: String, uid:Option[UUID], replyTo: ActorRef[Try[CredActionRes]]) extends Command
  final case class UpdateCred(cid:String,req:CredUpdateReq, replyTo: ActorRef[Try[Cred]]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: CredStore = new CredStoreMem

  def apply(store: CredStore): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: CredStore): Behavior[Command] = {
    this.store = store

    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    Behaviors.receiveMessage {
      case GetCreds(uid,replyTo) =>
        store.all.foreach(cs => replyTo ! Success(Creds(cs.filter(c => !uid.isDefined || (c.uid == uid.get) ))))
        Behaviors.same

      case CreateCred(req, uid, replyTo) =>
        val cid = Cred(
          req.cid,
          req.secret,
          req.name.getOrElse(""),
          uid = uid)

        store.+(cid)

        replyTo ! Success(cid)
        Behaviors.same

      case GetCred(cid, uid, replyTo) =>
        store.?(cid).onComplete {
          case Failure(e) => replyTo ! Failure(e)
          case Success(c) =>
            if(!uid.isDefined || (c.uid == uid.get)) replyTo ! Success(c)
            else replyTo ! Failure(new Exception(s"access denined: ${uid}"))
        }
        Behaviors.same

      case DeleteCred(cid, uid, replyTo) =>
        store.?(cid).onComplete {
          case Failure(e) => replyTo ! Failure(e)
          case Success(c) =>
            if(!uid.isDefined || (c.uid == uid.get)) {
              store.del(cid)
              replyTo ! Success(CredActionRes(s"deleted",Some(cid)))
            } else {
              replyTo ! Failure(new Exception(s"access denined: ${uid}"))
            }
        }
        Behaviors.same

      case UpdateCred(id, req, replyTo) =>
        store.update(id,req.secret,req.name,req.age).onComplete(replyTo ! _)
        Behaviors.same
    }
  }
}
