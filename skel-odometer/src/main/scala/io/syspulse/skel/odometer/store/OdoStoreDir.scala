package io.syspulse.skel.odometer.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.odometer.Odo
import io.syspulse.skel.odometer.server.OdoJson._

// Preload from file during start
class OdoStoreDir(dir:String = "store/") extends StoreDir[Odo,String](dir) with OdoStore {
  val store = new OdoStoreMem

  def toKey(id:String):String = id
  def all:Future[Seq[Odo]] = store.all
  def size:Future[Long] = store.size
  override def +(u:Odo):Future[Odo] = super.+(u).flatMap(_ => store.+(u))

  override def del(id:String):Future[String] = super.del(id).flatMap(_ => store.del(id))
  override def ?(id:String):Future[Odo] = store.?(id)

  override def update(id:String, counter:Long):Future[Odo] =
    store.update(id,counter).flatMap(o => {
      loading = true
      val r = Future.fromTry(writeFile(o))
      loading = false
      r
    })

  def ++(id:String, delta:Long):Future[Odo] =
    store.++(id,delta).flatMap(o => {
      loading = true
      val r = Future.fromTry(writeFile(o))
      loading = false
      r
    })

  override def clear():Try[OdoStoreDir] = super.clear().map(_ => this)

  // preload and watch
  load(dir)
  //watch(dir)
}
