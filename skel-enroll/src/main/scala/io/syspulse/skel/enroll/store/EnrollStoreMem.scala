package io.syspulse.skel.enroll.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.enroll.Enroll

class EnrollStoreMem extends EnrollStore {
  val log = Logger(s"${this}")
  
  var enrolls: Map[UUID,Enroll] = Map()

  def all:Seq[Enroll] = enrolls.values.toSeq

  def size:Long = enrolls.size

  def +(enroll:Enroll):Try[EnrollStore] = { 
    enrolls = enrolls + (enroll.id -> enroll)
    log.info(s"${enroll}")
    Success(this)
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

  def ?(id:UUID):Option[Enroll] = enrolls.get(id)

  def findByXid(xid:String):Option[Enroll] = {
    enrolls.values.find(_.xid == xid)
  }
}
