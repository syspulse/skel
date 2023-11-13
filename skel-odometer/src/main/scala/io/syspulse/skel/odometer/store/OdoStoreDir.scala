package io.syspulse.skel.odometer.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.odometer.Odo
import io.syspulse.skel.odometer.server.OdoJson._

// Preload from file during start
class OdoStoreDir(dir:String = "store/") extends StoreDir[Odo,String](dir) with OdoStore {
  val store = new OdoStoreMem

  def toKey(id:String):String = id
  def all:Seq[Odo] = store.all
  def size:Long = store.size
  override def +(u:Odo):Try[OdoStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)

  override def del(id:String):Try[OdoStoreDir] = super.del(id).flatMap(_ => store.del(id)).map(_ => this)
  override def ?(id:String):Try[Odo] = store.?(id)
  
  override def update(id:String, delta:Long):Try[Odo] = 
    store.update(id,delta).flatMap(u => writeFile(u))

  // preload and watch
  load(dir)
  watch(dir)
}