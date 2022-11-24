package io.syspulse.skel.telemetry.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.telemetry._
import io.syspulse.skel.store.Store

import io.syspulse.skel.telemetry.Config
import io.syspulse.skel.telemetry.Telemetry
import io.syspulse.skel.telemetry.Telemetry.ID

trait TelemetryStore extends Store[Telemetry,ID] {
  
  def +(telemetry:Telemetry):Try[TelemetryStore]
  def -(telemetry:Telemetry):Try[TelemetryStore]
  def del(id:ID):Try[TelemetryStore]
  def ?(id:ID,ts0:Long,ts1:Long):Seq[Telemetry]

  // Attention: returns only Head !
  def ?(id:ID):Option[Telemetry] =  ?(id,0L,Long.MaxValue).headOption

  def all:Seq[Telemetry]
  def size:Long

  def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry]

  // def connect(config:Config):TelemetryStore = this

  def scan(txt:String):Seq[Telemetry]
  def search(txt:String,ts0:Long,ts1:Long):Seq[Telemetry]
}
