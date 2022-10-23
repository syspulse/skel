package io.syspulse.skel.enroll.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command
import io.syspulse.skel.enroll._

object EnrollRegistry {
  val log = Logger(s"${this}")
  
  final case class GetEnrolls(replyTo: ActorRef[Enrolls]) extends Command
  final case class GetEnroll(id:UUID,replyTo: ActorRef[Option[Enroll]]) extends Command
  final case class GetEnrollByEmail(eid:String,replyTo: ActorRef[Option[Enroll]]) extends Command
  
  final case class CreateEnroll(enrollCreate: Option[EnrollCreateReq], replyTo: ActorRef[EnrollActionRes]) extends Command
  final case class DeleteEnroll(id: UUID, replyTo: ActorRef[EnrollActionRes]) extends Command
  
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
          replyTo ! Enrolls(store.all)
          Behaviors.same

        case GetEnroll(id, replyTo) =>
          // for {
          //     e <- EnrollSystem.summaryFuture(id)
          // } yield {
          //     log.info(s"e = ${e}")
          //     replyTo ! e.map( e => Enroll(e.eid,e.email.getOrElse(""),"",e.xid.getOrElse(""),e.tsPhase))
          // }
          for{
            e <- store.???(id)            
          } yield replyTo ! e          
          Behaviors.same

        case GetEnrollByEmail(email, replyTo) =>
          replyTo ! store.findByEmail(email)
          Behaviors.same

        case CreateEnroll(enrollCreate, replyTo) =>
          
          // val eid = EnrollSystem.start(
          //   "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
          //   Option(enrollCreate.xid)
          // )
          //   //Enroll(id, enrollCreate.email, enrollCreate.name, enrollCreate.eid, System.currentTimeMillis())

          val eid = store.+(enrollCreate.getOrElse(EnrollCreateReq()).xid)
          //val store1 = store.+(enroll)

          replyTo ! EnrollActionRes("started",eid.toOption)
          Behaviors.same
      
        case DeleteEnroll(id, replyTo) =>
          Behaviors.same
      }
    }}
  }
}
