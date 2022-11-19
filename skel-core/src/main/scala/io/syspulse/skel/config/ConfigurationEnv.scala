package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger


// Akka/Typesafe cofig suppors EnvVar, but the format is obtuse. 
// I prefer the Uppercase exact match without support for .
class ConfigurationEnv extends ConfigurationLike {

  def getString(path:String):Option[String] = { val e = System.getenv(path.replaceAll("\\.","_").toUpperCase); if(e == null) None else Some(e) }
  def getInt(path:String):Option[Int] = { val e = System.getenv(path.replaceAll("\\.","_").toUpperCase); if(e == null) None else Some(e.toInt) }
  def getLong(path:String):Option[Long] = { val e = System.getenv(path.replaceAll("\\.","_").toUpperCase); if(e == null) None else Some(e.toLong) }
  def getDuration(path:String):Option[Duration] = { val e = System.getenv(path.replaceAll("\\.","_").toUpperCase); if(e == null) None else Some(Duration.ofMillis(e.toLong)) }
  def getAll():Seq[(String,Any)] = {System.getenv().asScala.toSeq.map{ kv => (kv._1,kv._2)}}

  // not supported 
  def getParams():Seq[String] = Seq()

  // not supported
  def getCmd():Option[String] = None
}

