package io.syspulse.skel.user.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.user._
import scala.util.Try

object UserRegistry {
  val log = Logger(s"${this}")
  
  final case class GetUsers(replyTo: ActorRef[Users]) extends Command
  final case class GetUser(id:UUID,replyTo: ActorRef[Try[User]]) extends Command
  final case class GetUserByXid(xid:String,replyTo: ActorRef[Option[User]]) extends Command
  
  final case class CreateUser(req: UserCreateReq, replyTo: ActorRef[Option[User]]) extends Command
  final case class UpdateUser(uid:UUID, req: UserUpdateReq, replyTo: ActorRef[Try[User]]) extends Command
  final case class RandomUser(replyTo: ActorRef[User]) extends Command

  final case class DeleteUser(id: UUID, replyTo: ActorRef[UserActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: UserStore = null //new UserStoreDB //new UserStoreCache

  def apply(store: UserStore = new UserStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: UserStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetUsers(replyTo) =>
        replyTo ! Users(store.all)
        Behaviors.same

      case GetUser(id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case GetUserByXid(eid, replyTo) =>
        replyTo ! store.findByXid(eid)
        Behaviors.same


      case CreateUser(req, replyTo) =>
        val id = req.uid.getOrElse(UUID.randomUUID())

        val user = User(id, req.email, req.name, req.xid, req.avatar, System.currentTimeMillis())
        val store1 = store.+(user)

        replyTo ! Some(user)
        registry(store1.getOrElse(store))

      case UpdateUser(uid,req, replyTo) =>
        
        // val user:Option[User] = for {
        //   u <- store.?(uid)
        //   user1 <- Some(u.copy(email = req.email.getOrElse(u.email), name = req.name.getOrElse(u.name), avatar = req.avatar.getOrElse(u.avatar)))
        //   user2 <- if(store.+(user1).isSuccess) Some(user1) else None          
        // } yield user2
        val user = store.update(uid,req.email, req.name, req.avatar)

        replyTo ! user
        registry(store)

      case RandomUser(replyTo) =>
        
        //replyTo ! UserRandomRes(secret,qrImage)
        Behaviors.same
      
      case DeleteUser(id, replyTo) =>
        val store1 = store.del(id)
        replyTo ! UserActionRes(s"Success",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
