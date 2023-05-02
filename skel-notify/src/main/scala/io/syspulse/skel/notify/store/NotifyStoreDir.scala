package io.syspulse.skel.notify.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.notify.Notify
import io.syspulse.skel.notify.NotifyJson._
import io.syspulse.skel.notify.Config

// Preload from file during start
class NotifyStoreDir(dir:String = "store/")(implicit config:Config) extends StoreDir[Notify,UUID](dir) with NotifyStore {
  val store = new NotifyStoreMem()(config)

  def toKey(id:String):UUID = UUID(id)  
  def all:Seq[Notify] = store.all
  def size:Long = store.size

  override def del(id:UUID):Try[NotifyStore] = super.del(id)
  
  override def +(n:Notify):Try[NotifyStoreDir] = super.+(n).flatMap(_ => store.+(n)).map(_ => this)
  override def ?(id:UUID):Try[Notify] = store.?(id)
  override def ??(uid:UUID,fresh:Boolean):Seq[Notify] = store.??(uid,fresh)
  override def ack(id:UUID):Try[Notify] = store.ack(id).flatMap(n => writeFile(n))

  // preload and watch
  load(dir)
}