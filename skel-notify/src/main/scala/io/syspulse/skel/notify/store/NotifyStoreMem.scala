package io.syspulse.skel.notify.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify._

class NotifyStoreMem extends NotifyStore {
  val log = Logger(s"${this}")
  
  var notifys: Map[UUID,Notify] = Map()

  def all:Seq[Notify] = notifys.values.toSeq

  def size:Long = notifys.size

  def +(notify:Notify):Try[NotifyStore] = { 
    notifys = notifys + (notify.id -> notify)
    log.info(s"${notify}")
    Success(this)
  }

  def del(id:UUID):Try[NotifyStore] = { 
    val sz = notifys.size
    notifys = notifys - id;
    log.info(s"${id}")
    if(sz == notifys.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def -(notify:Notify):Try[NotifyStore] = {     
    del(notify.id)
  }

  def ?(id:UUID):Option[Notify] = notifys.get(id)

  def findByEid(eid:String):Option[Notify] = {
    notifys.values.find(_.eid == eid)
  }
}
