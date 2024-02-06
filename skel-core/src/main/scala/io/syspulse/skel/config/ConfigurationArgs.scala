package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger

import scopt.OParser

import io.syspulse.skel.util.Util

case class ConfigArgs() {
  var c:Map[String,Any] = Map()
  var cmd:Option[String] = None
  var params:Seq[String] = Seq()
  
  def +(k:String,v:Any):ConfigArgs = {
    c = c + (k -> v)
    this
  }

  def command(cmd:String):ConfigArgs = {
    this.cmd = Some(cmd)
    this
  }

  def param(p:String):ConfigArgs = {
    this.params = this.params :+ p
    this
  }

  override def toString = s"${c}"
}

trait Arg[T]
case class ArgString(argChar:Char,argStr:String,argText:String,default:String="") extends Arg[String]()
case class ArgInt(argChar:Char,argStr:String,argText:String,default:Int=0) extends Arg[Int]()
case class ArgLong(argChar:Char,argStr:String,argText:String,default:Long=0) extends Arg[Long]()
case class ArgDouble(argChar:Char,argStr:String,argText:String,default:Double=0.0) extends Arg[Double]()
case class ArgParam(argText:String,desc:String="") extends Arg[String]()
case class ArgHelp(argStr:String,desc:String="") extends Arg[String]()
case class ArgCmd(argStr:String,desc:String="") extends Arg[String]()
case class ArgLogging(argText:String = "logging level (INFO,ERROR,WARN,DEBUG)",default:String="") extends Arg[String]()

// Use "empty appName/appVersion for automatic inference"
class ConfigurationArgs(args:Array[String],appName:String,appVer:String,ops: Arg[_]*) extends ConfigurationLike {
  val log = Logger(s"${this}")

  def parseArgs(args:Array[String],ops: Arg[_]*) = {

    val builder = OParser.builder[ConfigArgs]
    val parser1 = {
      import builder._

      val ver = if(appVer.isEmpty) Util.info._2 else appVer
      val app = if(appName.isEmpty) Util.info._1 else appName

      val options = List(
        head(app, ver)
      ) ++ ops.flatMap(a => a match {
        case ArgCmd(s,t) => Some(cmd(s).action((x, c) => c.command(s)).text(t))
        case ArgHelp(s,t) => Some(help(s).text(t))
        case ArgString(c,s,t,d) => Some( (if(c=='_' || c==0) opt[String](s) else opt[String](c, s)).action((x, c) => c.+(s,x)).text(t))
        case ArgInt(c,s,t,d) => Some( (if(c=='_' || c==0) opt[Int](s) else opt[Int](c, s)).action((x, c) => c.+(s,x)).text(t))
        case ArgLong(c,s,t,d) => Some( (if(c=='_' || c==0) opt[Long](s) else opt[Long](c, s)).action((x, c) => c.+(s,x)).text(t))
        case ArgDouble(c,s,t,d) => Some( (if(c=='_' || c==0) opt[Double](s) else opt[Double](c, s)).action((x, c) => c.+(s,x)).text(t))
        case ArgParam(t,d) => Some(arg[String](t).unbounded().optional().action((x, c) => c.param(x)).text(d))
        case ArgLogging(t,d) => Some(opt[String](Configuration.LOGGING_ARG).action((x, c) => c.+(Configuration.LOGGING_ARG,x)).text(t))
        case _ => None
      }) ++ List(
        version("version"),
        help("help")
      )

      OParser.sequence(
        programName(app),
        options: _*
      )
    }

    OParser.parse(parser1, args, ConfigArgs())
  }

  def withExit(exitCode:Int):ConfigurationArgs = {
    if(! configArgs.isDefined) {
      System.exit(exitCode)
    }
    this
  }

  val configArgs = parseArgs(args,ops:_*)
  

  def getString(path:String):Option[String] = 
    if(!configArgs.isDefined) None else
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(v => Configuration.withEnv(v.asInstanceOf[String])) else None
  
  def getInt(path:String):Option[Int] = 
    if(!configArgs.isDefined) None else
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(_.asInstanceOf[Int]) else None

  def getLong(path:String):Option[Long] = 
    if(!configArgs.isDefined) None else
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(_.asInstanceOf[Long]) else None

  def getDouble(path:String):Option[Double] = 
    if(!configArgs.isDefined) None else
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(_.asInstanceOf[Double]) else None

  def getAll():Seq[(String,Any)] = {
    if(!configArgs.isDefined) return Seq()

    configArgs.get.c.toSeq
  }

  def getDuration(path:String):Option[Duration] = 
    if(!configArgs.isDefined) None else
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(v => Duration.ofMillis(v.asInstanceOf[Long])) else None

  def getParams():Seq[String] = {
    if(!configArgs.isDefined) return Seq()
    //configArgs.get.c.filter(_._2.asInstanceOf[Option[_]] == None).keySet.toSeq
    configArgs.get.params
  }

  // not supported
  def getCmd():Option[String] = {
    if(!configArgs.isDefined) return None
    configArgs.get.cmd
  }
}