package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger

// akka/typesafe config supoports System properties names
// If ConfigurationAkka is used, there is no need to include ConfigurationProp in chain
class ConfigurationProp extends ConfigurationLike {

  def getString(path:String):Option[String] = { val e = System.getProperty(path); if(e == null) None else Some(e) }
  def getInt(path:String):Option[Int] = { val e = System.getProperty(path); if(e == null) None else Some(e.toInt) }
  def getLong(path:String):Option[Long] = { val e = System.getProperty(path); if(e == null) None else Some(e.toLong) }

  // supports only millis
  def getDuration(path:String):Option[Duration] = { val e = System.getProperty(path); if(e == null) None else Some(Duration.ofMillis(e.toLong)) }

  def getAll():Seq[(String,Any)] = {System.getProperties.asScala.toSeq.map{ kv => (kv._1,kv._2)}}

  // not supported 
  def getParams():Seq[String] = Seq()

  // not supported
  def getCmd():Option[String] = None
}

