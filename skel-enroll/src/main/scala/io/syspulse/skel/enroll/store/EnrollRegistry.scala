package io.syspulse.skel.enroll.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command
import io.syspulse.skel.enroll._
import scala.util.Try
import scala.util.Failure

object EnrollRegistry {
  val log = Logger(s"${this}")

  final case class GetEnrolls(replyTo: ActorRef[Enrolls]) extends Command
  final case class GetEnroll(id:UUID,replyTo: ActorRef[Try[Enroll]]) extends Command
  final case class GetEnrollByEmail(eid:String,replyTo: ActorRef[Option[Enroll]]) extends Command

  final case class CreateEnroll(enrollCreate: Option[EnrollCreateReq], replyTo: ActorRef[Try[Enroll]]) extends Command
  final case class DeleteEnroll(id: UUID, replyTo: ActorRef[EnrollActionRes]) extends Command

  final case class UpdateEnroll(enrollUpdate: EnrollUpdateReq, replyTo: ActorRef[Option[Enroll]]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: EnrollStore = null //new EnrollStoreMem //new EnrollStoreCache

  def apply(store: EnrollStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: EnrollStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receive { (ctx,msg) => {
      implicit val ec = ctx.executionContext
      msg match {
        case GetEnrolls(replyTo) =>
          store.all.foreach(enrolls => replyTo ! Enrolls(enrolls))
          Behaviors.same

        case GetEnroll(id, replyTo) =>
          store.?(id).map(e => replyTo ! Try(e)).recover { case ex => replyTo ! Failure(ex) }
          Behaviors.same

        case GetEnrollByEmail(email, replyTo) =>
          store.findByEmail(email).foreach(e => replyTo ! e)
          Behaviors.same

        case CreateEnroll(enrollCreate, replyTo) =>
          enrollCreate match {
            case Some(EnrollCreateReq(email,name,xid,avatar)) =>
              store.+(xid,email,name,avatar).flatMap { eid =>
                store.?(eid)
              }.map { e =>
                replyTo ! Try(e)
              }.recover { case ex =>
                replyTo ! Failure(ex)
              }
            case None =>
              replyTo ! Failure(new Exception(s"could not create: ${enrollCreate}"))
          }
          Behaviors.same

        case UpdateEnroll(enrollUpdate, replyTo) =>
          for {
            e <- store.update(enrollUpdate.id,enrollUpdate.command.getOrElse(""),enrollUpdate.data)
          } replyTo ! e

          Behaviors.same

        case DeleteEnroll(id, replyTo) =>
          Behaviors.same
      }
    }}
  }
}
