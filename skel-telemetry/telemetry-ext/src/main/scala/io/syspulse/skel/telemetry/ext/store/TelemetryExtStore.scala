package io.syspulse.skel.telemetry.ext.store

import scala.util.{Failure,Success,Try}
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.Store
import io.syspulse.skel.telemetry.ext._

trait TelemetryExtStore extends Store[TelemetryChain,String] {
  private val log = Logger(getClass)
  
  def getKey(t: TelemetryChain): String = t.key
    
  def ???(k:String,oid:Option[String]):Future[TelemetryChain]
      
  def ?(k:String):Future[TelemetryChain] = ???(k,None)    
}
