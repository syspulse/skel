package io.syspulse.skel.lake.job

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

case class Job(
  id:UUID = Util.UUID_0,
  name:String = "",
  xid:String = "",
  src:String = "",
  inputs:Map[String,String] = Map(),
  uid:Option[UUID] = None,

  state:String = "unknown",
  ts0:Long = System.currentTimeMillis,
  
  log:Option[Seq[String]] = None,
  tsStart:Option[Long] = None,
  tsEnd:Option[Long] = None,  
  result:Option[String] = None,
  output:Option[String] = None,
  
)

object Job {
  type ID = String
}
