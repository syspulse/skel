package io.syspulse.skel.service

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class ServiceStoreMem extends ServiceStore {
  val log = Logger(s"${this}")
  
  var services: Set[Service] = Set()

  def all:Seq[Service] = services.toSeq
  def size:Long = services.size

  def +(service:Service):Try[ServiceStore] = { services = services + service; Success(this)}
  
  def del(id:UUID):Try[ServiceStore] = { 
    services.find(_.id == id) match {
      case Some(service) => { services = services - service; Success(this) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def -(service:Service):Try[ServiceStore] = { 
    val sz = services.size
    services = services - service;
    if(sz == services.size) Failure(new Exception(s"not found: ${service}")) else Success(this)
  }

  def ?(id:UUID):Option[Service] = services.find(_.id == id)
}
