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

  def +(o:Odo):Try[Odo] = { 
    odometers = odometers + (o.id -> o)
    log.debug(s"add: ${o}")
    Success(o)
  }

  def del(id:String):Try[String] = { 
    val sz = odometers.size
    odometers = odometers - id;
    log.info(s"del: ${id}")
    if(sz == odometers.size) Failure(new Exception(s"not found: ${id}")) else Success(id)  
  }

  def ?(id:String):Try[Odo] = odometers.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def update(id:String,counter:Long):Try[Odo] = {
    this.?(id) match {
      case Success(o) => 
        val o1 = modify(o,counter)
        this.+(o1)
        Success(o1)
      case f => f
    }
  }

  def ++(id:String, delta:Long):Try[Odo] = {
    this.?(id) match {
      case Success(o) => 
        val o1 = o.copy(counter = o.counter + delta, ts = System.currentTimeMillis)
        this.+(o1)        
        Success(o1)
      case f => f
    }
  }

  def clear():Try[OdoStore] = {
    odometers = Map()
    Success(this)
  }
  
  // support for namespaces
  // only 1 level namespace is supported
  override def ??(ids:Seq[String]):Seq[Odo] = {
    val oo = ids.flatMap( id => {
      id.split(":").toList match {
        case ns :: "*" :: Nil => 
          odometers.filter{ case(k,v) => k.startsWith(ns)}.values.toSeq
        case ns :: key :: Nil => 
          odometers.get(id) match {
            case Some(o) => Seq(o)
            case _ => Seq()
          }
        case key :: Nil =>
          odometers.get(key) match {
            case Some(o) => Seq(o)
            case _ => Seq()
          }
      }
    })
    oo    
  }
}
