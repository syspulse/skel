package io.syspulse.skel.service

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class ServiceStoreMem extends ServiceStore {
  val log = Logger(s"${this}")

  var services: Set[Service] = Set()

  def all:Future[Seq[Service]] = Future.successful(services.toSeq)
  def size:Future[Long] = Future.successful(services.size)

  def +(service:Service):Future[Service] = { services = services + service; Future.successful(service) }

  def del(id:UUID):Future[UUID] = {
    services.find(_.id == id) match {
      case Some(service) => { services = services - service; Future.successful(id) }
      case None => Future.failed(new Exception(s"not found: ${id}"))
    }
  }

  def ?(id:UUID):Future[Service] = services.find(_.id == id) match {
    case Some(s) => Future.successful(s)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }
}
