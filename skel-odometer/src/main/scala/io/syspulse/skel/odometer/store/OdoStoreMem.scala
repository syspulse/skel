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

class OdoStoreMem extends OdoStore {
  val log = Logger(s"${this}")
  
  var odometers: Map[String,Odo] = Map()

  def all:Seq[Odo] = odometers.values.toSeq

  def size:Long = odometers.size

  def +(o:Odo):Try[OdoStore] = { 
    odometers = odometers + (o.id -> o)
    log.info(s"add: ${o}")
    Success(this)
  }

  def del(id:String):Try[OdoStore] = { 
    val sz = odometers.size
    odometers = odometers - id;
    log.info(s"del: ${id}")
    if(sz == odometers.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:String):Try[Odo] = odometers.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def update(id:String,delta:Long):Try[Odo] = {
    this.?(id) match {
      case Success(o) => 
        val o1 = modify(o,delta)
        this.+(o1)
        Success(o1)
      case f => f
    }
  }

  def clear():Try[OdoStore] = {
    odometers = Map()
    Success(this)
  }
}
