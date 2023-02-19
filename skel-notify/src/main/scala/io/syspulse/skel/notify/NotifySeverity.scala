package io.syspulse.skel.notify

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

object NotifySeverity {
  type ID = Int
  
  val CRITICAL = 0
  val ALERT = 1
  val WARNING = 3
  val INFO = 5
}



