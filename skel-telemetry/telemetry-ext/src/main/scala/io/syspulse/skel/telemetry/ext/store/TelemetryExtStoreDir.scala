package io.syspulse.skel.telemetry.ext.store

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.telemetry.ext._
import io.syspulse.skel.telemetry.ext.TelemetryExtJson._
import scala.concurrent.ExecutionContext

// Preload from file during start
class TelemetryExtStoreDir(dir:String = "store/") extends StoreDir[TelemetryChain,String](dir) with TelemetryExtStore {
  val store = new TelemetryExtStoreMem
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def ???(k:String,oid:Option[String]):Future[TelemetryChain] = store.???(k,oid)

  def toKey(id:String):String = id
  def all:Future[Seq[TelemetryChain]] = store.all
  def size:Future[Long] = store.size
  override def +(u:TelemetryChain):Future[TelemetryChain] = super.+(u).flatMap(_ => store.+(u))

  override def del(id:String):Future[String] = super.del(id).flatMap(_ => store.del(id))
  override def ?(id:String):Future[TelemetryChain] = store.?(id)
    
  override def clear():Try[TelemetryExtStoreDir] = super.clear().map(_ => this)

  // preload and watch
  load(dir)

}