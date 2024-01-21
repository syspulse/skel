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

  override def del(id:UUID):Try[UUID] = store.del(id).map(_ => id)
  
  // Attention: call + after notify here !
  override def notify(n:Notify):Try[Notify] = {
    for {
      _ <- `++`(n)
      _ <- store.broadcast(n)
    } yield n    
  }
  override def +(n:Notify):Try[Notify] = {
    super.+(n).flatMap(_ => store.+(n))
  }

  override def ++(n:Notify):Try[Notify] = {
    for {
      n1 <- store.++(n)
      _ <- {
        super.+(n1)
      }
    } yield n1
  }

  override def ?(id:UUID):Try[Notify] = store.?(id)
  override def ??(uid:UUID,fresh:Boolean):Seq[Notify] = store.??(uid,fresh)
  override def ack(id:UUID):Try[Notify] = store.ack(id).flatMap(n => writeFile(n))



  // preload and watch
  load(dir)
}