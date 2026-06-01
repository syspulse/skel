package io.syspulse.skel.enroll.store

import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import io.jvm.uuid._

import io.syspulse.skel.enroll._

class EnrollStoreAkka(implicit val ec:ExecutionContext,config:Config) extends EnrollStore {
  val log = Logger(s"${this}")

  val EnrollSystem = new EnrollSystem()(config)

  def all:Future[Seq[Enroll]] = Future.successful(Seq())

  def size:Future[Long] = Future.successful(0L)

  def +(xid:Option[String],name:Option[String]=None,email:Option[String]=None,avatar:Option[String]=None):Future[UUID] = {
    val eid = EnrollSystem.start(
      "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
      xid,name,email,avatar
    )
    Future.successful(eid)
  }

  def del(id:UUID):Future[UUID] = {
    Future.failed(new Exception(s"not supported"))
  }

  def ?(id:UUID):Future[Enroll] = {
    EnrollSystem.summaryFuture(id).flatMap {
      case Some(e) =>
        log.info(s"e = ${e}")
        Future.successful(Enroll(
          e.eid,
          e.email.getOrElse(""),
          e.name.getOrElse(""),
          e.xid.getOrElse(""),
          e.avatar.getOrElse(""),
          e.tsPhase, e.phase, e.uid
        ))
      case None => Future.failed(new Exception(s"not found: ${id}"))
    }
  }

  def findByEmail(email:String):Future[Option[Enroll]] = {
    Future.successful(None)
  }

  def addEmail(id:UUID,email:String):Future[Option[Enroll]] = {
    EnrollSystem.addEmail(id,email)
    ?(id).map(Some(_))
  }

  def confirmEmail(id:UUID,code:String):Future[Option[Enroll]] = {
    EnrollSystem.confirmEmail(id,code)
    ?(id).map(Some(_))
  }
}
