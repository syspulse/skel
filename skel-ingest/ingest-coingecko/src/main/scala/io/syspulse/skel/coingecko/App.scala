package io.syspulse.skel.coingecko

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import java.util.concurrent.TimeUnit

import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

case class Config(  
  cmd:String = "coins",

  params: Seq[String] = Seq(),
)

object App {
  def main(args:Array[String]): Unit = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")

    val d = Config()

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"ingest-coingecko","",        
        
        ArgCmd("pipeline","Create pipeline"),
        ArgCmd("coins","Ask Coins"),
        
        ArgParam("<params>",""),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
            
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
        
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global    

    val r = config.cmd match {
      case "coins" =>   
        val uri = config.params.headOption.getOrElse("cg://")
        val ids = config.params.drop(1).toSet
        
        val cg = Coingecko(uri)    
        cg.get.askCoins(ids)
      
      case "pipeline" | "flow" =>         
        val uri = config.params.headOption.getOrElse("cg://")
        val ids = config.params.drop(1).toSet
        
        val cg = Coingecko(uri)    
        cg.get.source(ids)
    }

    Console.err.println(s"r = ${r}")
    //sys.exit(0)
  }
}
