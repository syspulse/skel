package io.syspulse.skel.enroll.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import io.jvm.uuid._
import io.syspulse.skel.enroll.Enroll

class EnrollStoreMem(implicit val ec:ExecutionContext) extends EnrollStore {
  val log = Logger(s"${this}")
  
  var enrolls: Map[UUID,Enroll] = Map()

  def all:Seq[Enroll] = enrolls.values.toSeq

  def size:Long = enrolls.size

  def +(xid:Option[String],name:Option[String]=None,email:Option[String]=None,avatar:Option[String]=None):Try[UUID] = {
    val id = UUID.random
    val enroll = Enroll(id = id,name=name.getOrElse(""),email=email.getOrElse(""),avatar=avatar.getOrElse(""),tsCreated=System.currentTimeMillis(), xid = xid.getOrElse(""))
    enrolls = enrolls + (enroll.id -> enroll)
    log.info(s"${enroll}")
    Success(id)
  }

  def del(id:UUID):Try[EnrollStore] = { 
    val sz = enrolls.size
    enrolls = enrolls - id;
    log.info(s"${id}")
    if(sz == enrolls.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }
  def -(enroll:Enroll):Try[EnrollStore] = {     
    del(enroll.id)
  }

  def ???(id:UUID):Future[Try[Enroll]] = Future(
    enrolls.get(id) match {
      case Some(e) => Success(e)
      case None => Failure(new Exception(s"not found: ${id}"))
    }
  )

  def findByEmail(email:String):Option[Enroll] = {
    enrolls.values.find(_.email == email)
  }

  def addEmail(id:UUID,email:String):Future[Option[Enroll]] = {
    Future(None)
  }

  def confirmEmail(id:UUID,code:String):Future[Option[Enroll]] = {
    Future(None)
  }
}
