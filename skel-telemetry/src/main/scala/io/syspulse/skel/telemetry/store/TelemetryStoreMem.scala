package io.syspulse.skel.telemetry.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.telemetry._

class TelemetryStoreMem extends TelemetryStore {
  val log = Logger(s"${this}")
  
  var telemetrys: Map[Telemetry.ID,Telemetry] = Map()

  def all:Seq[Telemetry] = telemetrys.values.toSeq

  def size:Long = telemetrys.size

  def +(t:Telemetry):Try[TelemetryStore] = { 
    telemetrys = telemetrys + (t.id -> t)
    log.info(s"${t}")
    Success(this)
  }

  def del(vid:Telemetry.ID):Try[TelemetryStore] = { 
    val sz = telemetrys.size
    telemetrys = telemetrys - vid;
    log.info(s"${vid}")
    if(sz == telemetrys.size) Failure(new Exception(s"not found: ${vid}")) else Success(this)  
  }

  def -(t:Telemetry):Try[TelemetryStore] = {     
    del(t.id)
  }

  def ?(id:Telemetry.ID):Option[Telemetry] = telemetrys.get(id)

  def ??(txt:String):List[Telemetry] = {
    telemetrys.values.filter(t => {
      t.id.matches(txt)
      //||
      //v.desc.matches(txt)
    }
    ).toList
  }

  def scan(txt:String):List[Telemetry] = ??(txt)
  def search(txt:String):List[Telemetry] = ??(txt + ".*")  
}
