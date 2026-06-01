package io.syspulse.skel.notify.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.{StoreDir,Store}

import io.syspulse.skel.notify.Notify
import io.syspulse.skel.notify.NotifyJson._
import io.syspulse.skel.notify.Config

// Preload from file during start
class NotifyStoreDir(dir:String = "store/")(implicit config:Config) extends StoreDir[Notify,UUID](dir) with NotifyStore {
  val store = new NotifyStoreMem()(config)

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def toKey(id:String):UUID = UUID(id)
  def all:Future[Seq[Notify]] = store.all
  def size:Future[Long] = store.size

  override def del(id:UUID):Future[UUID] = store.del(id)

  // Attention: call ++ before notify here !
  override def notify(n:Notify):Future[Notify] = {
    for {
      _ <- `++`(n)
      _ <- Store.toFuture(store.broadcast(n))
    } yield n
  }

  override def +(n:Notify):Future[Notify] =
    super.+(n).flatMap(_ => store.+(n))

  override def ++(n:Notify):Future[Notify] = {
    for {
      n1 <- store.++(n)
      _  <- super.+(n1)
    } yield n1
  }

  override def ?(id:UUID):Future[Notify] = store.?(id)
  override def ??(uid:UUID,fresh:Boolean):Future[Seq[Notify]] = store.??(uid,fresh)
  override def ack(id:UUID):Future[Notify] = store.ack(id).flatMap(n => Future.fromTry(writeFile(n)))

  // preload and watch
  load(dir)
}
