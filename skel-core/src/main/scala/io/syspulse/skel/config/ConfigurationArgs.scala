package io.syspulse.skel.config

import java.time.Duration

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger

import scopt.OParser

import io.syspulse.skel.util.Util
import java.io.File
import com.typesafe.config.Config
import io.syspulse.skel.config
import scopt.DefaultOEffectSetup
import scopt.OParserSetup
import scopt.DefaultOParserSetup

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
case class ArgConfig(argText:String = "Configuration file",default:String="") extends Arg[String]()
case class ArgUnknown() extends Arg[String]() // parameter to memorize unknown args

// Use "empty appName/appVersion for automatic inference"
class ConfigurationArgs(args:Array[String],appName:String,appVer:String,ops: Arg[_]*) extends ConfigurationLike {
  val log = Logger(s"${this}")

  def parseArgs(args:Array[String],ops: Arg[_]*) = {
    var errorOnUnknown = true
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
        case ArgConfig(t,d) => Some(opt[String](Configuration.CONFIG_ARG).action((x, c) => {
            import com.typesafe.config.Config
            import com.typesafe.config.ConfigFactory            
            
            try {
              log.info(s"Loading config: '${x}'...")
              if(!os.exists(os.Path(x,os.pwd))) throw new Exception(s"file not found: '${x}'")

              //val baseConfig = ConfigFactory.load()
              //val conf1 = ConfigFactory.parseFile(new File(x)).withFallback(baseConfig)
              val conf1 = ConfigFactory.parseFile(new File(x))
              log.info(s"Config: '${x}': ${conf1}")
              overrideConfig = Some(new ConfigurationAkkaOverride(conf1))
            } catch {
              case e: Exception => {
                log.error(s"failed to load Configuration: '${x}': ",e)
                throw e
              }
            }
            
            c.+(Configuration.CONFIG_ARG,x)
          }).text(t))
        case ArgUnknown() => 
          errorOnUnknown = false
          None
        case _ => 
          log.warn(s"unknown Arg option: ${a}")
          None
      }) ++ List(
        version("version"),
        help("help")
      )

      OParser.sequence(
        programName(app),
        options: _*
      )
    }
    
    OParser.runParser(parser1, args, ConfigArgs(),
      new DefaultOParserSetup {
        override def errorOnUnknownArgument: Boolean = errorOnUnknown
      }) match {
      case (result, effects) => 
        var errMsg = ""
        OParser.runEffects(effects, new DefaultOEffectSetup {
          // override def displayToOut(msg: String): Unit = Console.out.println(msg)
          override def displayToErr(msg: String): Unit = {
            Console.err.println(msg)
            //()
          }

          override def reportWarning(msg: String): Unit = {            
            log.warn(s"${msg}")
            // dirty hack to support memoization
            msg match {
              case opt if(msg.startsWith("Unknown option")) =>
                val kv = msg.stripPrefix("Unknown option").trim
                kv.split("--|=").tail.toList match {
                  case k :: v :: Nil =>
                    memory.+(k,v)
                  case _ =>
                    log.warn(s"failed to parse opt: ${kv}")
                }
              case _ => 
                // just error
            }
            errMsg = errMsg + msg
          }

          override def reportError(msg: String): Unit = displayToErr("Error: " + msg)
          
          // ignore terminate
          override def terminate(exitState: Either[String, Unit]): Unit = ()
        })

        result match {
          case Some(config) =>
            Some(config)
          case _ =>      
            None
        }
    }

  }

  def withExit(exitCode:Int):ConfigurationArgs = {
    if(! configArgs.isDefined) {
      System.exit(exitCode)
    }
    this
  }

  // fallback memory
  var memory = new ConfigurationMap()
  var overrideConfig:Option[ConfigurationAkkaOverride] = None
  val configArgs = parseArgs(args,ops:_*)
  
  def getString(path:String):Option[String] = {
    val r0 = memory.getString(path)
    if(r0.isDefined) return r0

    val r = if(overrideConfig.isDefined) overrideConfig.get.getString(path) else None
    if(!configArgs.isDefined) {
      return r
    }
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(v => Configuration.withEnv(v.asInstanceOf[String])) else r
  }
  
  def getInt(path:String):Option[Int] = {
    val r0 = memory.getInt(path)
    if(r0.isDefined) return r0

    val r = if(overrideConfig.isDefined) overrideConfig.get.getInt(path) else None
    if(!configArgs.isDefined) {
      return r
    }
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(_.asInstanceOf[Int]) else r
  }

  def getLong(path:String):Option[Long] = {    
    val r0 = memory.getLong(path)
    if(r0.isDefined) return r0

    val r = if(overrideConfig.isDefined) overrideConfig.get.getLong(path) else None
    if(!configArgs.isDefined) {
      return r
    }
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(_.asInstanceOf[Long]) else r
  }

  def getDouble(path:String):Option[Double] = {
    val r0 = memory.getDouble(path)
    if(r0.isDefined) return r0

    val r = if(overrideConfig.isDefined) overrideConfig.get.getDouble(path) else None
    if(!configArgs.isDefined) {
      return r
    }
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(_.asInstanceOf[Double]) else r
  }

  def getDuration(path:String):Option[Duration] = {
    val r0 = memory.getDuration(path)
    if(r0.isDefined) return r0

    val r = if(overrideConfig.isDefined) overrideConfig.get.getDuration(path) else None
    if(!configArgs.isDefined) {
      return r
    }
    if (configArgs.get.c.contains(path)) configArgs.get.c.get(path).map(v => Duration.ofMillis(v.asInstanceOf[Long])) else r
  }

  def getAll():Seq[(String,Any)] = {
    val r0 = memory.getAll()
    
    val r = if(overrideConfig.isDefined) overrideConfig.get.getAll() else Seq() 

    val r2 = if(!configArgs.isDefined) {
      Seq()
    }
    else 
      configArgs.get.c.toSeq

    // replace old with new
    (r.toMap ++ r2.toMap).toSeq ++ r0
  }


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