package io.syspulse.skel.telemetry.store

import scala.util.Try
import scala.util.Success
import scala.util.Failure

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.telemetry._
import io.syspulse.skel.store.Store

import io.syspulse.skel.telemetry.Config
import io.syspulse.skel.telemetry.Telemetry
import io.syspulse.skel.telemetry.Telemetry.ID
import io.syspulse.skel.telemetry.server.Telemetrys

trait TelemetryStore extends Store[Telemetry,ID] {
  def getKey(t: Telemetry): ID = t.id

  def clean():Try[TelemetryStore]
  def +(telemetry:Telemetry):Try[Telemetry]
  def del(id:ID):Try[ID]
  
  // return sorted
  def ?(id:ID,ts0:Long,ts1:Long,op:Option[String] = None):Seq[Telemetry]

  def ??(id:ID,ts0:Long,ts1:Long,op:Option[String]):Option[Telemetry] = {
    val tt = ?(id,ts0,ts1,op)
    val d = op match {
      case Some("avg") => tt.foldLeft(0.0)((r,t) => r + t.data.asInstanceOf[List[String]].headOption.getOrElse("0.0").toDouble) / tt.size
      case Some("sum") => tt.foldLeft(0.0)((r,t) => r + t.data.asInstanceOf[List[String]].headOption.getOrElse("0.0").toDouble)
      case Some("last") => if(tt.size==0) 0.0 else tt.last.data.asInstanceOf[List[String]].headOption.getOrElse("0.0").toDouble
      case Some("first") => if(tt.size==0) 0.0 else tt.head.data.asInstanceOf[List[String]].headOption.getOrElse("0.0").toDouble
      case _ => tt.foldLeft(0.0)((r,t) => r + t.data.asInstanceOf[List[String]].headOption.getOrElse("0.0").toDouble) / tt.size
    }
    Some(Telemetry(id,0L,List(d)))
  }


  // Attention: returns last by default
  def ?(id:ID):Try[Telemetry] =  last(id)

  def last(id:ID):Try[Telemetry] = ?(id,0L,Long.MaxValue).sortBy(- _.ts).headOption match {
    case Some(t) => Success(t)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def all:Seq[Telemetry]
  def ???(ts0:Long,ts1:Long,from:Option[Int],size:Option[Int]):Telemetrys

  def size:Long

  def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry]

  // def connect(config:Config):TelemetryStore = this

  def scan(txt:String):Seq[Telemetry]
  def search(txt:String,ts0:Long,ts1:Long):Seq[Telemetry]
}
