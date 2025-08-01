package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger

trait ConfigurationLike {

  def getString(path:String):Option[String] 
  def getInt(path:String):Option[Int]
  def getLong(path:String):Option[Long]
  def getDouble(path:String):Option[Double]
  def getDuration(path:String):Option[Duration]
  def getAll():Seq[(String,Any)]
  def getParams():Seq[String]
  def getCmd():Option[String] 
}

class Configuration(configurations: Seq[ConfigurationLike]) extends ConfigurationLike {

  def convertBackslash(s:String) = s
    .replace("\\n","\n")
    .replace("\\r","\r")

  def getSmartString(path:String):Option[String] = {  
    val v = getString(path)
    v.flatMap(_.trim.split("://").toList match {
      case "file" :: file => 
        Some(os.read(os.Path(file.head,os.pwd)))
      case _ =>
        v
    })
  }

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

  def getDouble(path:String):Option[Double] = {
    configurations.foldLeft[Option[Double]](None)((r,c) => if(r.isDefined) r else c.getDouble(path))
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

  def getListString(path:String,d:Seq[String] = Seq(),empty:Boolean = false, trim:Boolean = true,comment:Option[String] = None):Seq[String] = {
    val v = getString(path)
    val s = if(v.isDefined) v.get else d.mkString(",")
    
    val data = if(s.trim.startsWith("file://")) {
      scala.io.Source.fromFile(s.drop("file://".size)).getLines().mkString(",")      
    } else {
      s
    }

    // if comments are supported, need to find lines regardless of delimiter
    val data1 = if(comment.isDefined) {
      data.split("\n")
         .filter(s => ! s.trim.startsWith(comment.get))
         .mkString("\n")
    }
    else 
      data

    data1.split(",")
      .map(s => if(trim) s.trim else s)
      .filter(empty || !_.isEmpty)
      .filter(s => !comment.isDefined || !s.trim.startsWith(comment.get))
      .toSeq
  }

  def getMap(path:String,d:Map[String,String] = Map()):Map[String,String] = {
    val v = getString(path)
    if(!v.isDefined) return d
    
    v.get.split(",").map(s => s.split("=").toList match {
      case key :: value :: Nil => (key.trim,value)
      case _ => (s,s)
    }).toMap    
  }

  def getListStringDumb(path:String,d:Seq[String] = Seq()):Seq[String] = {
    val v = getString(path)
    val s = if(v.isDefined) v.get else d.mkString(",")    
    s.split(",").map(_.trim).filter(!_.isEmpty).toSeq    
  }

  def getListLong(path:String):Seq[Long] = getListString(path).flatMap( s => s.toLongOption)
  def getListInt(path:String):Seq[Int] = getListString(path).flatMap( s => s.toIntOption)

  def withLogging():Configuration = { 
    import org.slf4j.LoggerFactory
    import ch.qos.logback.classic.{Logger,Level}
    
    getString(Configuration.LOGGING_ARG).map(loggers => 
      // format: class:level,class:level
      // if class is not specified, it is root
      loggers.split(",").foreach( lg => {
        val (name,level) = lg.split(":").toList match {
          case name :: level :: Nil => (name,level)
          case level :: Nil => ("root",level)
          case _ => ("root","INFO")
        }
        LoggerFactory.getLogger(name).asInstanceOf[Logger].setLevel(Level.valueOf(level))
      })      
    )
    this
  }
}

object Configuration {
  val LOGGING_ARG = "log"
  val CONFIG_ARG = "conf"
  
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