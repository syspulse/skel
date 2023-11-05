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

object UserRegistryAsync {
  val log = Logger(s"${this}")

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    
  import UserRegistryProto._
  
  def apply(store: UserStoreAsync): Behavior[io.syspulse.skel.Command] = {
    registry(store)
  }

  private def registry(store: UserStoreAsync): Behavior[io.syspulse.skel.Command] = {
    
    Behaviors.receiveMessage {
      case GetUsers(replyTo) =>
        store.all.map(r => replyTo ! Users(r))
        Behaviors.same

      case GetUser(id, replyTo) =>
        val r = store.?(id)
        r.onComplete(replyTo ! _)
        Behaviors.same

      case GetUserByXid(eid, replyTo) =>
        val r = store.findByXid(eid)
        r.onComplete( r => r match {
          case f @ Failure(e) => 
            log.warn(s"user not found: ${eid}")
            replyTo ! None
          case Success(u) => replyTo ! Some(u)
        })
        Behaviors.same

      case CreateUser(req, replyTo) =>
        val id = req.uid.getOrElse(UUID.randomUUID())

        val store1 = 
          store.?(id).onComplete(_ match {
            case Success(_) => 
              replyTo ! Failure(new Exception(s"already exists: ${id}"))
              
            case _ =>  
              val user = User(id, req.email, req.name, req.xid, req.avatar, System.currentTimeMillis())
              val store1 = store.+(user)
              
              store1.onComplete(r => r match {
                case f @ Failure(e) => replyTo ! Failure(e)
                case _ => replyTo ! Success(user)
              })
          })
        
        Behaviors.same

      case UpdateUser(uid,req, replyTo) =>
        val r = store.update(uid,req.email, req.name, req.avatar)

        r.onComplete(replyTo ! _)
        Behaviors.same
      
      case DeleteUser(id, replyTo) =>
        val r = store.del(id)
        r.onComplete(r => r match {
          case Success(_) => replyTo ! UserActionRes("200",Some(id))
          case Failure(e) => replyTo ! UserActionRes("619",Some(id))
        })

        Behaviors.same
    }
  }
}
