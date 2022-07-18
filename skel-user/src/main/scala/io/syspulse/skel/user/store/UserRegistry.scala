package io.syspulse.skel.user.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.user._

object UserRegistry {
  val log = Logger(s"${this}")
  
  final case class GetUsers(replyTo: ActorRef[Users]) extends Command
  final case class GetUser(id:UUID,replyTo: ActorRef[Option[User]]) extends Command
  final case class GetUserByEid(eid:String,replyTo: ActorRef[Option[User]]) extends Command
  
  final case class CreateUser(userCreate: UserCreateReq, replyTo: ActorRef[User]) extends Command
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

      case GetUserByEid(eid, replyTo) =>
        replyTo ! store.findByEid(eid)
        Behaviors.same


      case CreateUser(userCreate, replyTo) =>
        val id = userCreate.uid.getOrElse(UUID.randomUUID())

        val user = User(id, userCreate.email, userCreate.name, userCreate.eid, System.currentTimeMillis())
        val store1 = store.+(user)

        replyTo ! user
        registry(store1.getOrElse(store))

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
