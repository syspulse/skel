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

class EnrollStoreAkka(implicit val ec:ExecutionContext) extends EnrollStore {
  val log = Logger(s"${this}")
  
  def all:Seq[Enroll] = Seq()

  def size:Long = 0L

  def +(xid:Option[String]):Try[UUID] = { 
    val eid = EnrollSystem.start(
      "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
      xid
    )
    //Enroll(id, enrollCreate.email, enrollCreate.name, enrollCreate.eid, System.currentTimeMillis())
    
    Success(eid)
  }

  def del(id:UUID):Try[EnrollStore] = { 
    //log.info(s"del: ${id}")
    Failure(new Exception(s"not supported"))
  }

  def -(enroll:Enroll):Try[EnrollStore] = {     
    del(enroll.id)
  }

  def ???(id:UUID):Future[Option[Enroll]] = {
    for {
        e <- EnrollSystem.summaryFuture(id)
    } yield {
      log.info(s"e = ${e}")
      e.map( e => Enroll(e.eid,e.email.getOrElse(""),"",e.xid.getOrElse(""),e.tsPhase, e.phase, e.uid))   
    }
  }

  def findByEmail(email:String):Option[Enroll] = {
    None
  }
}
