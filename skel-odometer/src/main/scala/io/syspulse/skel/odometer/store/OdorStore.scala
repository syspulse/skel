package io.syspulse.skel.odometer.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.odometer._
import io.syspulse.skel.store.Store

import io.syspulse.skel.odometer.Odo

trait OdoStore extends Store[Odo,String] {
  
  def getKey(e: Odo): String = e.id
  def +(odometer:Odo):Try[OdoStore]
  
  def del(id:String):Try[OdoStore]
  def ?(id:String):Try[Odo]  
  def all:Seq[Odo]
  def size:Long
  
  def update(id:String, counter:Long):Try[Odo]

  def ++(id:String, delta:Long):Try[Odo]

  def clear():Try[OdoStore]

  protected def modify(o:Odo, counter:Long):Odo = {    
    (for {
      o0 <- Some(o.copy(ts = System.currentTimeMillis))
      o1 <- Some(o.copy(counter = counter))
    } yield o1).get    
  }
}
