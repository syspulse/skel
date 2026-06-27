package io.syspulse.skel.telemetry.ext.store

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.ErrNotFound
import io.syspulse.skel.telemetry.ext._

// ============================================================================================================================

class TelemetryExtStoreMem extends TelemetryExtStore {
  private val log = Logger(s"${this}")
  
  var telemetrys: Map[String,TelemetryChain] = Map()
  
  def all():Future[Seq[TelemetryChain]] = Future.successful(telemetrys.values.toSeq)

  def size:Future[Long] = Future.successful(telemetrys.size)

  def +(t:TelemetryChain):Future[TelemetryChain] = {     
    telemetrys = telemetrys + (t.key -> t)
    Future.successful(t)
  }
  override def ?(k:String):Future[TelemetryChain] = {
    telemetrys.get(k) match {
      case Some(t) => Future.successful(t)
      case None => Future.failed(new ErrNotFound(s"not found: ${k}"))
    }
  }

  def ???(k:String,oid:Option[String]):Future[TelemetryChain] = {
    telemetrys.get(k) match {
      case Some(t) => Future.successful(t)
      case None => Future.failed(new ErrNotFound(s"not found: ${k}"))
    }
  }

  def del(id:String):Future[String] = {
    telemetrys = telemetrys - id
    Future.successful(id)
  }
 
  def clear():Future[Unit] = {
    telemetrys = Map()
    Future.successful(())
  }
}


