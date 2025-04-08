package io.syspulse.skel.twitter

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.twitter.TwitterConnect
import java.util.concurrent.TimeUnit

case class Config(  
  cmd:String = "ask",

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
      new ConfigurationArgs(args,"ingest-twitter","",        
        
        ArgCmd("connect","Connect to twitter via Uri (e.g. twitter://key:secret@channel)"),
        ArgCmd("ask","Ask channel"),
        
        ArgParam("channels"),

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
    implicit val timeout = FiniteDuration(5000L,TimeUnit.MILLISECONDS)

    val r = config.cmd match {
      case "connect" =>         
        val connect = new TwitterConnect(config.params(0))    
        connect.ask(config.params.drop(1).toSet)

      case "ask" =>         
        val connect = new TwitterConnect("twitter://${CONSUMER_KEY}:${CONSUMER_SECRET}?freq=6000000")    
        connect.ask(config.params.toSet)
    }

    Console.err.println(s"r = ${r}")
    //sys.exit(0)
  }
}
