package io.syspulse.skel.telemetry.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.telemetry._
import io.syspulse.skel.telemetry.server.Telemetrys

class TelemetryStoreMem extends TelemetryStore {
  val log = Logger(s"${this}")
  
  var telemetrys: immutable.TreeMap[Long,List[Telemetry]] = immutable.TreeMap()
  // Map[Telemetry.ID,Telemetry] = Map()

  def clean():Try[TelemetryStore] = { telemetrys = immutable.TreeMap(); Success(this); }
  def all:Seq[Telemetry] = telemetrys.values.flatten.toSeq

  def size:Long = telemetrys.values.flatten.size

  def ???(ts0:Long,ts1:Long,from:Option[Int]=None,size:Option[Int]=None):Telemetrys = {
    val ts2 = if(ts1 == Long.MaxValue) ts1 else ts1 + 1    
    val tt = telemetrys
      .range(ts0,ts2)
      .values
      .flatten
      .toSeq
    Telemetrys(tt.drop(from.getOrElse(0)).take(size.getOrElse(Int.MaxValue)),total = Some(tt.size))
  }

  def +(t:Telemetry):Try[Telemetry] = { 
    log.debug(s"${t}")

    // avoid duplicates
    val d = telemetrys.get(t.ts).map(_.find(_.data == t.data)).flatten
    
    if(!d.isDefined)
      telemetrys = telemetrys + (t.ts -> {telemetrys.getOrElse(t.ts,List()) :+ t})
    
    Success(t)
  }

  def del(id:Telemetry.ID):Try[Telemetry.ID] = { 
    val r = telemetrys.values.flatten.filter(_.id == id).map( t => {
      telemetrys = telemetrys - t.ts
      true
    })
    log.debug(s"${id}")
    if(r.size > 0)
      Success(id)
    else
      Failure(new Exception(s"not found: ${id}"))
  }

  def ?(id:Telemetry.ID,ts0:Long,ts1:Long,op:Option[String] = None):Seq[Telemetry] = {
    log.info(s"id=${id},ts=(${ts0},${ts1})")
    val ts2 = if(ts1 == Long.MaxValue) ts1 else ts1 + 1
    telemetrys.range(ts0,ts2).values.flatten.filter(_.id == id).toSeq
  }

  def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = {
    val ts2 = if(ts1 == Long.MaxValue) ts1 else ts1 + 1
    telemetrys.range(ts0,ts2).values.flatten.filter(t => {
      t.id.matches(txt)
      //||
      //v.desc.matches(txt)
    }
    ).toSeq
  }

  def scan(txt:String):Seq[Telemetry] = ??(txt,0L,Long.MaxValue)
  def search(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = ??(txt + ".*",ts0,ts1)
}
