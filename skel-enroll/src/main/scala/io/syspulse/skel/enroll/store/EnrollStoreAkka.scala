package io.syspulse.skel.enroll.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import io.jvm.uuid._

import io.syspulse.skel.enroll._

class EnrollStoreAkka(implicit val ec:ExecutionContext,config:Config) extends EnrollStore {
  val log = Logger(s"${this}")
  
  val EnrollSystem = new EnrollSystem()(config)
  def all:Seq[Enroll] = Seq()

  def size:Long = 0L

  def +(xid:Option[String],name:Option[String]=None,email:Option[String]=None,avatar:Option[String]=None):Try[UUID] = { 
    val eid = EnrollSystem.start(
      "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
      xid,name,email,avatar
    )
    //Enroll(id, enrollCreate.email, enrollCreate.name, enrollCreate.eid, System.currentTimeMillis())
    
    Success(eid)
  }

  def del(id:UUID):Try[UUID] = {     
    Failure(new Exception(s"not supported"))
  }

  def ???(id:UUID):Future[Try[Enroll]] = {
    for {
        e <- EnrollSystem.summaryFuture(id)
    } yield {
      log.info(s"e = ${e}")
      e match {
        case Some(e) => 
          Success(Enroll(
            e.eid,
            e.email.getOrElse(""),
            e.name.getOrElse(""),
            e.xid.getOrElse(""),
            e.avatar.getOrElse(""), 
            e.tsPhase, e.phase, e.uid
          ))
        case None => Failure(new Exception(s"not found: ${id}"))
      }
    }
  }

  def findByEmail(email:String):Option[Enroll] = {
    None
  }

  def addEmail(id:UUID,email:String):Future[Option[Enroll]] = {
    EnrollSystem.addEmail(id,email)
    ???(id).map(_.toOption)
  }

  def confirmEmail(id:UUID,code:String):Future[Option[Enroll]] = {
    EnrollSystem.confirmEmail(id,code)
    ???(id).map(_.toOption)
  }
}
