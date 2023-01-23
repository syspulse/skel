package io.syspulse.skel.enroll.store

import scala.util.Try
import scala.util.Success

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.enroll.Enroll
import scala.concurrent.ExecutionContext


trait EnrollStore extends Store[Enroll,UUID] {
  implicit val ec:ExecutionContext
  
  def getKey(e: Enroll): UUID = e.id

  def update(id:UUID,command:String,data:Map[String,String]):Future[Option[Enroll]] = {
    command match {
      case "email" =>
        addEmail(id,data("email"))
      case "confirm" => 
        confirmEmail(id,data("code"))
      case _ => Future(None)
    }
  }

  def addEmail(id:UUID,email:String):Future[Option[Enroll]]
  def confirmEmail(id:UUID,code:String):Future[Option[Enroll]]

  def +(e:Enroll):Try[EnrollStore] = {
    this.+(Option(e.xid))
    Success(this)
  }

  def +(xid:Option[String],name:Option[String]=None,email:Option[String]=None,avatar:Option[String]=None):Try[UUID]
  
  def del(id:UUID):Try[EnrollStore]
  def ?(id:UUID):Try[Enroll] = {
    Await.result(???(id),FiniteDuration(1000L, TimeUnit.MILLISECONDS))
  }

  def ???(id:UUID):Future[Try[Enroll]]

  def all:Seq[Enroll]
  def size:Long

  def findByEmail(email:String):Option[Enroll]
}

