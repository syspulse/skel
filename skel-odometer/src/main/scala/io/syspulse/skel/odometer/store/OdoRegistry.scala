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

import scala.concurrent.ExecutionContext

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

  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

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
        store.all.onComplete {
          case Success(oo) => replyTo ! Success(Odos(oo, total = Some(oo.size)))
          case Failure(e)  =>
            log.error("failed to get all", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case GetOdo(id, replyTo) =>
        store.??(Seq(id)).onComplete {
          case Success(oo) => replyTo ! Success(Odos(oo, total = Some(oo.size)))
          case Failure(e)  =>
            log.error(s"failed to get: ${id}", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case CreateOdo(req, replyTo) =>
        store.?(req.id).onComplete {
          case Success(_) =>
            replyTo ! Failure(new Exception(s"already exists: ${req.id}"))
          case Failure(_) =>
            val o = Odo(req.id, req.counter.getOrElse(0L))
            store.+(o).onComplete {
              case Success(_) => replyTo ! Success(Odos(Seq(o), total = Some(1)))
              case Failure(e) => replyTo ! Failure(e)
            }
        }
        Behaviors.same

      case UpdateOdo(req, replyTo) =>
        // ATTENTION: Update is ++ !
        store.++(req.id, req.delta).onComplete {
          case Success(o) =>
            replyTo ! Success(Odos(Seq(o), total = Some(1)))
          case Failure(_) =>
            // try to create
            val o = Odo(req.id, 0L)
            store.+(o).onComplete {
              case Success(o) => replyTo ! Success(Odos(Seq(o), total = Some(1)))
              case Failure(e) => replyTo ! Failure(e)
            }
        }
        Behaviors.same

      case DeleteOdo(id, replyTo) =>
        store.del(id).onComplete {
          case Success(_) => replyTo ! Success(id)
          case Failure(e) => replyTo ! Failure(e)
        }
        Behaviors.same

    }
  }
}
