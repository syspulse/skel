package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger

trait ConfigurationLike {

  def getString(path:String):Option[String] 
  def getInt(path:String):Option[Int]
  def getLong(path:String):Option[Long]
  def getDuration(path:String):Option[Duration]
  def getAll():Seq[(String,Any)]
}

class Configuration(configurations: Seq[ConfigurationLike]) extends ConfigurationLike {
  def getString(path:String):Option[String] = {
    configurations.foldLeft[Option[String]](None)((r,c) => if(r.isDefined) r else c.getString(path))
  }
  
  def getInt(path:String):Option[Int] = {
    configurations.foldLeft[Option[Int]](None)((r,c) => if(r.isDefined) r else c.getInt(path))
  }

  def getLong(path:String):Option[Long] = {
    configurations.foldLeft[Option[Long]](None)((r,c) => if(r.isDefined) r else c.getLong(path))
  }

  def getAll():Seq[(String,Any)] = {
    configurations.foldLeft[Seq[(String,Any)]](Seq())((r,c) => r ++ c.getAll())
  }

  def getDuration(path:String):Option[Duration] = {
    configurations.foldLeft[Option[Duration]](None)((r,c) => if(r.isDefined) r else c.getDuration(path))
  }
  
}

object Configuration {
  // automatically support Akka-stype EnvVar
  System.setProperty("config.override_with_env_vars","true")
  def apply():Configuration = new Configuration(Seq(new ConfigurationAkka))

  // last has the highest priority, so because of foldLeft, reverse
  def withPriority(configurations: Seq[ConfigurationLike]):Configuration = new Configuration(configurations.reverse)

  // default: try config in this sequence, later supercese earlier (Env > Prop > Akk)
  def default:Configuration = Configuration.withPriority(Seq(new ConfigurationAkka,new ConfigurationProp,new ConfigurationEnv))

}