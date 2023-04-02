package io.syspulse.skel.lake.job

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

case class Job(
  xid:String,
  state:String,
  ts0:Long = System.currentTimeMillis,
  src:String,
  log:Option[Seq[String]] = None,
  tsStart:Option[Long] = None,
  tsEnd:Option[Long] = None,  
  result:Option[String] = None,
  output:Option[String] = None,  
)
