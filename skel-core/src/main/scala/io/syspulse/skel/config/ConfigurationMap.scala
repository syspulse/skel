package io.syspulse.skel.config

import java.time.Duration
import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger


class ConfigurationMap extends ConfigurationLike {
  var data:Map[String,String] = Map()

  def +(k:String,v:String) = {
    data = data + (k -> v)
  }

  def getString(path:String):Option[String] = data.get(path).map(_.toString)
  def getInt(path:String):Option[Int] = data.get(path).map(_.toInt)
  def getLong(path:String):Option[Long] = data.get(path).map(_.toLong)
  
  def getDouble(path:String):Option[Double] = data.get(path).map(_.toDouble)

  // supports only millis
  def getDuration(path:String):Option[Duration] = data.get(path).map(v => Duration.ofMillis(v.toLong))

  def getAll():Seq[(String,Any)] = data.toSeq

  // not supported 
  def getParams():Seq[String] = Seq()
  // not supported
  def getCmd():Option[String] = None
}

