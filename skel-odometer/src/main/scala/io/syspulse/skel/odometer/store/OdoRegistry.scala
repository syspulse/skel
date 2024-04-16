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
  final case class GetOdos(replyTo: ActorRef[Try[Odos]]) extends Command
  final case class GetOdo(id:String,replyTo: ActorRef[Try[Odos]]) extends Command
  
  final case class CreateOdo(req: OdoCreateReq, replyTo: ActorRef[Try[Odos]]) extends Command
  final case class UpdateOdo(req: OdoUpdateReq, replyTo: ActorRef[Try[Odos]]) extends Command  
  final case class DeleteOdo(id: String, replyTo: ActorRef[Try[String]]) extends Command
}

object OdoRegistry {  
  val log = Logger(s"${this}")
  
  import OdoRegistryProto._
  
  // this var reference is unfortunately needed for Metrics access
  var store: OdoStore = null 

  def apply(store: OdoStore = new OdoStoreMem): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: OdoStore): Behavior[Command] = {    
    this.store = store
    
    Behaviors.receiveMessage {
      case GetOdos(replyTo) =>
        try {
          val oo = store.all
          replyTo ! Success(Odos(oo,total=Some(oo.size)))
        } catch {
          case e:Exception => 
            log.error("failed to get all",e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case GetOdo(id, replyTo) =>
        try {
          val oo = store.??(Seq(id))
          replyTo ! Success( Odos(oo,total=Some(oo.size)) )
        } catch {
          case e:Exception =>
            log.error(s"failed to get: ${id}",e)
            replyTo ! Failure(e)
        }
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
              replyTo ! store1.map(_ => Odos(Seq(o),total=Some(1))) 
          }

        Behaviors.same

      case UpdateOdo(req, replyTo) =>        
        // ATTENTION: Update is ++ !
        val o = store.++(req.id,req.delta)
        val r = o match {
          case Success(o) => Success(Odos(Seq(o),total=Some(1)))
          case Failure(e) => 
            // try to create
            val o = Odo(req.id, 0L)
            store.+(o).map(o => Odos(Seq(o),total=Some(1)))
        }
        replyTo ! r

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
