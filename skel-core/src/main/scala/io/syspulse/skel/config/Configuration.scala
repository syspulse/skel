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
  def getParams():Seq[String]
  def getCmd():Option[String] 
}

class Configuration(configurations: Seq[ConfigurationLike]) extends ConfigurationLike {

  def convertBackslash(s:String) = s
    .replace("\\n","\n")
    .replace("\\r","\r")

  // support for global reference to another string
  def getString(path:String):Option[String] = {  
    val r = configurations.foldLeft[Option[String]](None)((r,c) => if(r.isDefined) r else c.getString(path))
    
    val s = if(r.isDefined && r.get.startsWith("@")) 
      getString(r.get.tail)
    else 
      r
    s.map(convertBackslash(_))
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

  def getParams():Seq[String] = {
    configurations.foldLeft[Seq[String]](Seq())((r,c) => r ++ c.getParams())
  }

  def getCmd():Option[String] = {
    configurations.foldLeft[Option[String]](None)((r,c) => if(r.isDefined) r else c.getCmd())
  }

  def getListString(path:String,d:Seq[String] = Seq()):Seq[String] = {
    val v = getString(path)
    val s = if(v.isDefined) v.get else d.mkString(",")
    
    if(s.trim.startsWith("file://")) {
      val data = scala.io.Source.fromFile(s.drop("file://".size)).getLines().mkString(",")
      data.split(",").map(_.trim).filter(!_.isEmpty).toSeq
    } else {
      s.split(",").map(_.trim).filter(!_.isEmpty).toSeq
    }
  }

  def getListLong(path:String):Seq[Long] = getListString(path).flatMap( s => s.toLongOption)
  def getListInt(path:String):Seq[Int] = getListString(path).flatMap( s => s.toIntOption)
}

object Configuration {
  // automatically support Akka-stype EnvVar
  System.setProperty("config.override_with_env_vars","true")
  def apply():Configuration = new Configuration(Seq(new ConfigurationAkka))

  // last has the highest priority, so because of foldLeft, reverse
  def withPriority(configurations: Seq[ConfigurationLike]):Configuration = new Configuration(configurations.reverse)

  // default: try config in this sequence, later supercese earlier (Env > Prop > Akk)
  def default:Configuration = Configuration.withPriority(Seq(new ConfigurationAkka,new ConfigurationProp,new ConfigurationEnv))

  def withEnv(value:String):String = {
    val env = value.split("\\$\\{").filter(_.contains("}")).map(s => s.substring(0,s.indexOf("}"))).toList
    val envPairs = env.map(s => (s,sys.env.get(s).getOrElse("${"+s+"}")))

    envPairs.foldLeft(value)( (value,pair) => { value.replace("${"+pair._1+"}",pair._2) })
  } 
}