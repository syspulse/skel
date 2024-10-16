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

  def +(service:Service):Try[Service] = { services = services + service; Success(service)}
  
  def del(id:UUID):Try[UUID] = { 
    services.find(_.id == id) match {
      case Some(service) => { services = services - service; Success(id) }
      case None => Failure(new Exception(s"not found: ${id}"))
    }
    
  }
  def ?(id:UUID):Try[Service] = services.find(_.id == id) match {
    case Some(s) => Success(s)
    case None => Failure(new Exception(s"not found: ${id}"))
  }
}
