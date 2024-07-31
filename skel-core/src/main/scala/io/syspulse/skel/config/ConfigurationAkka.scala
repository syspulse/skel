package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

// Akka/Typesafe config supports EnvVar with -Dconfig.override_with_env_vars=true
// Var format: CONFIG_FORCE_{var}. CASE-SENSITIVE !
// Ignore if application.conf cannot be loaded
class ConfigurationAkka extends ConfigurationTypesafe {

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
}

class ConfigurationAkkaOverride(config:Config) extends ConfigurationTypesafe {
  var akkaConfig:Option[Config] = Some(config)
}


trait ConfigurationTypesafe extends ConfigurationLike {
  val log = Logger(s"${this}")

  var akkaConfig:Option[Config]

  def getString(path:String):Option[String] = 
    try {
      if(!akkaConfig.isDefined) None else
      if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getString(path)) else None
    } catch {
      case e:Exception => log.warn(s"${e}"); None
    }
  
  def getInt(path:String):Option[Int] = 
    try {
      if(!akkaConfig.isDefined) None else
      if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getInt(path)) else None
    } catch {
      case e:Exception => log.warn(s"${e}"); None
    }

  def getLong(path:String):Option[Long] = 
    try {
      if(!akkaConfig.isDefined) None else
      if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getLong(path)) else None
    } catch {
      case e:Exception => log.warn(s"${e}"); None
    }
  
  def getDouble(path:String):Option[Double] = 
    try {
      if(!akkaConfig.isDefined) None else
      if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getDouble(path)) else None
    } catch {
      case e:Exception => log.warn(s"${e}"); None
    }

  def getAll():Seq[(String,Any)] = {
    if(!akkaConfig.isDefined) return Seq()

    // ATTENTION: unwraps values !
    akkaConfig.get.entrySet().asScala.toSeq.map(es => (es.getKey(),es.getValue.unwrapped))
  }

  def getDuration(path:String):Option[Duration] = 
    try {
      if(!akkaConfig.isDefined) None else
      if (akkaConfig.get.hasPath(path)) Some(akkaConfig.get.getDuration(path)) else None
    } catch {
      case e:Exception => log.warn(s"${e}"); None
    }
  
    // not supported 
  def getParams():Seq[String] = Seq()

  // not supported
  def getCmd():Option[String] = None
}
