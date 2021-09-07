package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

trait ConfigurationLike {

  def getString(path:String):Option[String] 
  def getInt(path:String):Option[Int]
  def getLong(path:String):Option[Long]
  def getDuration(path:String):Option[Duration]
  def getAll():Seq[(String,Any)]
}

// Akka/Typesafe cofig suppors EnvVar, but the format is obtuse. 
// I prefer the Uppercase exact match without support for .
class ConfigurationEnv extends ConfigurationLike {

  def getString(path:String):Option[String] = { val e = System.getenv(path.toUpperCase); if(e == null) None else Some(e) }
  def getInt(path:String):Option[Int] = { val e = System.getenv(path.toUpperCase); if(e == null) None else Some(e.toInt) }
  def getLong(path:String):Option[Long] = { val e = System.getenv(path.toUpperCase); if(e == null) None else Some(e.toLong) }
  def getDuration(path:String):Option[Duration] = { val e = System.getenv(path.toUpperCase); if(e == null) None else Some(Duration.ofMillis(e.toLong)) }
  def getAll():Seq[(String,Any)] = {System.getenv().asScala.toSeq.map{ kv => (kv._1,kv._2)}}
}

// akka/typesafe config supoports System properties names
// If ConfigurationAkka is used, there is no need to include ConfigurationProp in chain
class ConfigurationProp extends ConfigurationLike {

  def getString(path:String):Option[String] = { val e = System.getProperty(path); if(e == null) None else Some(e) }
  def getInt(path:String):Option[Int] = { val e = System.getProperty(path); if(e == null) None else Some(e.toInt) }
  def getLong(path:String):Option[Long] = { val e = System.getProperty(path); if(e == null) None else Some(e.toLong) }

  // supports only millis
  def getDuration(path:String):Option[Duration] = { val e = System.getProperty(path); if(e == null) None else Some(Duration.ofMillis(e.toLong)) }

  def getAll():Seq[(String,Any)] = {System.getProperties.asScala.toSeq.map{ kv => (kv._1,kv._2)}}
}

// Akka/Typesafe config supports EnvVar with -Dconfig.override_with_env_vars=true
// Var format: CONFIG_FORCE_{var}. CASE-SENSITIVE !
// Ignore if application.conf cannot be loaded
class ConfigurationAkka extends ConfigurationLike {
  val log = Logger(s"${this}")

  //withFallback(ConfigFactory.defaultReference(classLoader)
  var akkaConfig:Option[Config] = {
    try {
      Some(ConfigFactory.load())
    } catch {
      case e @ (_ : com.typesafe.config.ConfigException.IO | _ : Exception) => {
        log.error(s"Configuration not loaded: ",e)
        // try to load default ?!
        System.setProperty("config.resource","application.conf")
        Some(ConfigFactory.defaultReference(this.getClass.getClassLoader))
      }
    }
  }

  def getString(path:String):Option[String] = 
    if(!akkaConfig.isDefined) None else
    if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getString(path)) else None
  
  def getInt(path:String):Option[Int] = 
    if(!akkaConfig.isDefined) None else
    if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getInt(path)) else None

  def getLong(path:String):Option[Long] = 
    if(!akkaConfig.isDefined) None else
    if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getLong(path)) else None

  def getAll():Seq[(String,Any)] = {
    if(!akkaConfig.isDefined) return Seq()

    akkaConfig.get.entrySet().asScala.toSeq.map(es => (es.getKey(),es.getValue.toString))
  }

  def getDuration(path:String):Option[Duration] = 
    if(!akkaConfig.isDefined) None else
    if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getDuration(path)) else None
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

  def withPriority(configurations: Seq[ConfigurationLike]):Configuration = new Configuration(configurations)

  def default:Configuration = Configuration.apply()

}