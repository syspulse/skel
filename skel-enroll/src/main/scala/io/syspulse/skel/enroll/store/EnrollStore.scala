package io.syspulse.skel.enroll.store

import scala.util.Try
import scala.util.Success

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.enroll.Enroll


trait EnrollStore extends Store[Enroll,UUID] {
  implicit val ec:ExecutionContext

  def getKey(e: Enroll): UUID = e.id

  def update(id:UUID,command:String,data:Map[String,String]):Future[Option[Enroll]] = {
    command match {
      case "email" =>
        addEmail(id,data("email"))
      case "confirm" =>
        confirmEmail(id,data("code"))
      case _ => Future.successful(None)
    }
  }

  def addEmail(id:UUID,email:String):Future[Option[Enroll]]
  def confirmEmail(id:UUID,code:String):Future[Option[Enroll]]

  def +(e:Enroll):Future[Enroll] = {
    this.+(Option(e.xid)).map(_ => e)
  }

  def +(xid:Option[String],name:Option[String]=None,email:Option[String]=None,avatar:Option[String]=None):Future[UUID]

  def del(id:UUID):Future[UUID]
  def ?(id:UUID):Future[Enroll]

  def all:Future[Seq[Enroll]]
  def size:Future[Long]

  def findByEmail(email:String):Future[Option[Enroll]]
}
