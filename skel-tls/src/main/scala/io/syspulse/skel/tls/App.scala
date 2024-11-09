package io.syspulse.skel.tls

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

case class Config(

  cmd:String = "resolve",
  params: Seq[String] = Seq(),
)

object App {
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-tls","",
                               
        ArgCmd("resolve","Resolve SSL"),
        
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
        
    val r = config.cmd match {    
      case "resolve" => 
        config.params.toList match {
          case domain :: Nil  =>
            SslUtil.resolve(domain)
          case _ => 
            SslUtil.resolve("google.com")
        }
    }
    Console.err.println(s"r = ${r}")
  }
}
