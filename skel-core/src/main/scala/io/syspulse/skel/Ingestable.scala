package io.syspulse.skel

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

trait Ingestable extends Product {
  def toLog:String = toCSV
  def toSimpleLog:String = toString()
  def toCSV:String = Util.toCSV(this)
  def getIndex:Option[Any] = None
}