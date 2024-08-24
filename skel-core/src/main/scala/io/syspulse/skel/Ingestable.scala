package io.syspulse.skel

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

trait Ingestable extends Product {
  
  // it is possible to convert to binary here
  def toRaw:Array[Byte] = toString.getBytes()

  def toLog:String = toCSV
  def toSimpleLog:String = toString()
  def toCSV:String = Util.toCSV(this)
  def getId:Option[Any] = getKey
  def getKey:Option[Any] = None
}