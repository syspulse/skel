package io.syspulse.skel.odometer.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.odometer.Odo

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class OdoStoreMem extends OdoStore {
  val log = Logger(s"${this}")

  var odometers: Map[String,Odo] = Map()

  def all:Future[Seq[Odo]] = Future.successful(odometers.values.toSeq)

  def size:Future[Long] = Future.successful(odometers.size.toLong)

  def +(o:Odo):Future[Odo] = {
    odometers = odometers + (o.id -> o)
    log.debug(s"add: ${o}")
    Future.successful(o)
  }

  def del(id:String):Future[String] = {
    val sz = odometers.size
    odometers = odometers - id;
    log.info(s"del: ${id}")
    if(sz == odometers.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:String):Future[Odo] = odometers.get(id) match {
    case Some(u) => Future.successful(u)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def update(id:String,v:Long):Future[Odo] = {
    implicit val ec = ExecutionContext.global
    this.?(id).flatMap { o =>
      val o1 = modify(o,v)
      this.+(o1).map(_ => o1)
    }
  }

  def ++(id:String, delta:Long):Future[Odo] = {
    implicit val ec = ExecutionContext.global
    this.?(id).flatMap { o =>
      val o1 = o.copy(v = o.v + delta, ts = System.currentTimeMillis)
      this.+(o1).map(_ => o1)
    }
  }

  def clear():Future[OdoStore] = {
    odometers = Map()
    Future.successful(this)
  }

  // support for namespaces
  // only 1 level namespace is supported
  override def ??(ids:Seq[String])(implicit ec:ExecutionContext):Future[Seq[Odo]] = {
    val oo = ids.flatMap( id => {
      id.split(":").toList match {
        case ns :: "*" :: Nil =>
          odometers.filter{ case(k,v) => k.startsWith(ns)}.values.toSeq
        case ns :: key :: Nil =>
          odometers.get(id) match {
            case Some(o) => Seq(o)
            case _ => Seq()
          }

        case "*" :: Nil => odometers.values.toSeq

        case key :: Nil =>
          odometers.get(key) match {
            case Some(o) => Seq(o)
            case _ => Seq()
          }
      }
    })
    Future.successful(oo)
  }
}
