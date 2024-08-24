package io.syspulse.skel.odometer.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.odometer._
import io.syspulse.skel.store.Store

import io.syspulse.skel.odometer.Odo

trait OdoStore extends Store[Odo,String] {
  
  def getKey(e: Odo): String = e.id
  def +(odometer:Odo):Try[Odo]
  
  def del(id:String):Try[String]
  def ?(id:String):Try[Odo]  
  def all:Seq[Odo]
  def size:Long
  
  def update(id:String, v:Long):Try[Odo]

  def ++(id:String, delta:Long):Try[Odo]

  def clear():Try[OdoStore]

  protected def modify(o:Odo,v:Long):Odo = {    
    (for {
      o1 <- Some(o.copy(ts = System.currentTimeMillis,v = v))
    } yield o1).get    
  }

  // this operator supports namespace "namespace:key"
  // Implementation should overwrite it if support fast version (like Redis)
  override def ??(ids:Seq[String]):Seq[Odo] = {
    if(ids.filter(_.contains(":*")).size != 0)
      throw new Exception(s"no implementation")
    else
      super.??(ids)
  }
}

