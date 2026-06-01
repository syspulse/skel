package io.syspulse.skel.enroll.store

import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import io.jvm.uuid._
import io.syspulse.skel.enroll.Enroll

class EnrollStoreMem(implicit val ec:ExecutionContext) extends EnrollStore {
  val log = Logger(s"${this}")

  var enrolls: Map[UUID,Enroll] = Map()

  def all:Future[Seq[Enroll]] = Future.successful(enrolls.values.toSeq)

  def size:Future[Long] = Future.successful(enrolls.size.toLong)

  def +(xid:Option[String],name:Option[String]=None,email:Option[String]=None,avatar:Option[String]=None):Future[UUID] = {
    val id = UUID.random
    val enroll = Enroll(id = id,name=name.getOrElse(""),email=email.getOrElse(""),avatar=avatar.getOrElse(""),tsCreated=System.currentTimeMillis(), xid = xid.getOrElse(""))
    enrolls = enrolls + (enroll.id -> enroll)
    log.info(s"${enroll}")
    Future.successful(id)
  }

  def del(id:UUID):Future[UUID] = {
    val sz = enrolls.size
    enrolls = enrolls - id
    log.info(s"${id}")
    if(sz == enrolls.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:UUID):Future[Enroll] = enrolls.get(id) match {
    case Some(e) => Future.successful(e)
    case None    => Future.failed(new Exception(s"not found: ${id}"))
  }

  def findByEmail(email:String):Future[Option[Enroll]] = {
    Future.successful(enrolls.values.find(_.email == email))
  }

  def addEmail(id:UUID,email:String):Future[Option[Enroll]] = {
    Future.successful(None)
  }

  def confirmEmail(id:UUID,code:String):Future[Option[Enroll]] = {
    Future.successful(None)
  }
}
