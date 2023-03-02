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

object CredRegistry {  
  final case class CreateCred(req: CredCreateReq, uid:UUID, replyTo: ActorRef[Try[Cred]]) extends Command
  final case class GetCred(cid: String, uid:Option[UUID], replyTo: ActorRef[Try[Cred]]) extends Command
  final case class GetCreds(uid:Option[UUID], replyTo: ActorRef[Try[Creds]]) extends Command
  final case class DeleteCred(cid: String, uid:Option[UUID], replyTo: ActorRef[Try[CredActionRes]]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: CredStore = new CredStoreMem

  def apply(store: CredStore): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: CredStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetCreds(uid,replyTo) =>
        replyTo ! Success(Creds(store.all.filter(c => !uid.isDefined || (c.uid == uid.get) )))
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
        replyTo ! {
          for {
            c <- store.?(cid)
            c2 <- if(!uid.isDefined || (c.uid == uid.get)) Success(c) else Failure(new Exception(s"access denined: ${uid}"))
          } yield c2
        }
        Behaviors.same

      case DeleteCred(cid, uid, replyTo) =>
        //val store1 = store.del(cid)
        //replyTo ! CredActionRes(s"success",Some(cid))

        replyTo ! {
          for {
            c <- store.?(cid)
            c2 <- if(!uid.isDefined || (c.uid == uid.get)) Success(c) else Failure(new Exception(s"access denined: ${uid}"))            
            r <- {
              store.del(cid)
              Success(CredActionRes(s"deleted",Some(cid)))
            }
          } yield r
        }

        Behaviors.same
    }
  }
}

