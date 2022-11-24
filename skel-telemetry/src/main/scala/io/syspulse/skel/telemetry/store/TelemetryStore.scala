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
  def ?(id:ID):Option[Telemetry]
  def all:Seq[Telemetry]
  def size:Long

  def ??(txt:String):List[Telemetry]

  // def connect(config:Config):TelemetryStore = this

  def scan(txt:String):List[Telemetry]
  def search(txt:String):List[Telemetry]
}
