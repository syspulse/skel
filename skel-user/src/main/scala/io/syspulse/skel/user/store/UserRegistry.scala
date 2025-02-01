package io.syspulse.skel.user.store

import scala.util.{Try,Success,Failure}

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
import akka.actor.typed.DispatcherSelector
import com.typesafe.config.ConfigFactory
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object UserRegistryProto {
  final case class GetUsers(replyTo: ActorRef[Users]) extends Command
  final case class GetUser(id:UUID,replyTo: ActorRef[Try[User]]) extends Command
  final case class GetUserByXid(xid:String,replyTo: ActorRef[Option[User]]) extends Command
  
  final case class CreateUser(req: UserCreateReq, replyTo: ActorRef[Try[User]]) extends Command
  final case class UpdateUser(uid:UUID, req: UserUpdateReq, replyTo: ActorRef[Try[User]]) extends Command
  final case class RandomUser(replyTo: ActorRef[User]) extends Command

  final case class DeleteUser(id: UUID, replyTo: ActorRef[UserActionRes]) extends Command
  
  final case class TestTimeout(timeout:Long,replyTo: ActorRef[UserActionRes]) extends Command
}

object UserRegistry {  
  val log = Logger(s"${this}")
  
  import UserRegistryProto._  
  
  // this var reference is unfortunately needed for Metrics access
  var store: UserStore = null 

  // Create custom ExecutionContext
  implicit val ec: ExecutionContextExecutor = {
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))
  }

  def apply(store: UserStore = new UserStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }
  
  private def registry(store: UserStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case TestTimeout(timeout,replyTo) =>
        // ATTENTION: Timeout !
        Future {
          log.info(s"TestTimeout: ${timeout} <-")
          Thread.sleep(timeout)
          log.info(s"TestTimeout: ${timeout} ->")
          replyTo ! UserActionRes("300",None)
        }
        Behaviors.same

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

        val store1 = 
          store.?(id) match {
            case Success(_) => 
              replyTo ! Failure(new Exception(s"already exists: ${id}"))
              Success(store)
            case _ =>  
              val user = User(id, req.email, req.name, req.xid, req.avatar, System.currentTimeMillis())
              val store1 = store.+(user)
              replyTo ! store1.map(_ => user)
              store1
          }
        
        Behaviors.same

      case UpdateUser(uid,req, replyTo) =>
        
        val user = store.update(uid,req.email, req.name, req.avatar)

        replyTo ! user
        Behaviors.same

      case RandomUser(replyTo) =>
        
        //replyTo ! UserRandomRes(secret,qrImage)
        Behaviors.same
      
      case DeleteUser(id, replyTo) =>
        val r = store.del(id)
        r match {
          case Success(_) => replyTo ! UserActionRes("200",Some(id))
          case Failure(e) => replyTo ! UserActionRes("619",Some(id))
        }
        Behaviors.same
    }
  }
}
