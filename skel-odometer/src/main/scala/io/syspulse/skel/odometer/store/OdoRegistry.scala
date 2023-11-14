package io.syspulse.skel.odometer.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.Command

import io.syspulse.skel.odometer._
import io.syspulse.skel.odometer.server.{Odos, OdoRes, OdoCreateReq, OdoUpdateReq}

object OdoRegistryProto {
  final case class GetOdos(replyTo: ActorRef[Odos]) extends Command
  final case class GetOdo(id:String,replyTo: ActorRef[Try[Odo]]) extends Command
  
  final case class CreateOdo(req: OdoCreateReq, replyTo: ActorRef[Try[Odo]]) extends Command
  final case class UpdateOdo(id:String, req: OdoUpdateReq, replyTo: ActorRef[Try[Odo]]) extends Command  
  final case class DeleteOdo(id: String, replyTo: ActorRef[Try[String]]) extends Command
}

object OdoRegistry {  
  val log = Logger(s"${this}")
  
  import OdoRegistryProto._
  
  // this var reference is unfortunately needed for Metrics access
  var store: OdoStore = null 

  def apply(store: OdoStore = new OdoStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: OdoStore): Behavior[io.syspulse.skel.Command] = {    
    this.store = store
    
    Behaviors.receiveMessage {
      case GetOdos(replyTo) =>
        replyTo ! Odos(store.all)
        Behaviors.same

      case GetOdo(id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same      

      case CreateOdo(req, replyTo) =>
        val store1 = 
          store.?(req.id) match {
            case Success(_) => 
              replyTo ! Failure(new Exception(s"already exists: ${req.id}"))
              Success(store)
            case _ =>  
              val o = Odo(req.id, req.counter.getOrElse(0L))
              val store1 = store.+(o)
              replyTo ! store1.map(_ => o) 
          }

        Behaviors.same

      case UpdateOdo(id, req, replyTo) =>        
        val o = store.update(id,req.delta)        
        replyTo ! o

        Behaviors.same
      
      case DeleteOdo(id, replyTo) =>
        val r = store.del(id)
        r match {
          case Success(o) => replyTo ! Success(id)
          case Failure(e) => replyTo ! Failure(e)
        }
        Behaviors.same
      
    }
  }
}
