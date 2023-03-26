package io.syspulse.skel.lake.job

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

case class Job(
  xid:String,
  status:String,
  src:String,
  log:Option[Seq[String]] = None,
  res:Option[String] = None
)
