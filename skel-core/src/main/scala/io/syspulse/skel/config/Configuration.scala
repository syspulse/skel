package io.syspulse.skel.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

trait ConfigurationLike {

  def getString(path:String):Option[String] 
  def getInt(path:String):Option[Int]
  def getLong(path:String):Option[Long]
}

// Akka/Typesafe cofig suppors EnvVar, but the format is obtuse. 
// I prefer the Uppercase exact match without support for .
class ConfigurationEnv extends ConfigurationLike {

  def getString(path:String):Option[String] = { val e = System.getenv(path.toUpperCase); if(e == null) None else Some(e) }
  def getInt(path:String):Option[Int] = { val e = System.getenv(path.toUpperCase); if(e == null) None else Some(e.toInt) }
  def getLong(path:String):Option[Long] = { val e = System.getenv(path.toUpperCase); if(e == null) None else Some(e.toLong) }
}

// akka/typesafe config supoports System properties names
// If ConfigurationAkka is used, there is no need to include ConfigurationProp in chain
class ConfigurationProp extends ConfigurationLike {

  def getString(path:String):Option[String] = { val e = System.getProperty(path); if(e == null) None else Some(e) }
  def getInt(path:String):Option[Int] = { val e = System.getProperty(path); if(e == null) None else Some(e.toInt) }
  def getLong(path:String):Option[Long] = { val e = System.getProperty(path); if(e == null) None else Some(e.toLong) }
}

// Akka/Typesafe config supports EnvVar with -Dconfig.override_with_env_vars=true
// Var format: CONFIG_FORCE_{var}. CASE-SENSITIVE !
class ConfigurationAkka extends ConfigurationLike {

  var akkaConfig:Option[Config] = Some(ConfigFactory.load())

  def getString(path:String):Option[String] = 
    if(!akkaConfig.isDefined) None else
    if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getString(path)) else None
  
  def getInt(path:String):Option[Int] = 
    if(!akkaConfig.isDefined) None else
    if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getInt(path)) else None

  def getLong(path:String):Option[Long] = 
    if(!akkaConfig.isDefined) None else
    if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getLong(path)) else None

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
}

object Configuration {
  // automatically support Akka-stype EnvVar
  System.setProperty("config.override_with_env_vars","true")
  def apply():Configuration = new Configuration(Seq(new ConfigurationAkka))

  def withPriority(configurations: Seq[ConfigurationLike]):Configuration = new Configuration(configurations)
}