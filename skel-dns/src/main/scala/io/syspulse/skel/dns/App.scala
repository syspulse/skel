package io.syspulse.skel.dns

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.FutureUtil._
import scala.concurrent.ExecutionContext

case class Config(
  cmd:String = "auto",
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
      new ConfigurationArgs(args,"skel-dns","",
                               
        ArgCmd("whois","Whois"),
        ArgCmd("rdap","RDAP"),
        ArgCmd("auto","Auto"),
        
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
        
    implicit val ec:ExecutionContext = scala.concurrent.ExecutionContext.global

    val f = config.cmd match {
      case "rdap" =>        
        config.params.toList match {
          case domain :: Nil  =>
            new RdapResolver(None).resolve(domain)
          case domain :: server :: Nil  =>
            new RdapResolver(Some(server)).resolve(domain)
          case _ => 
            new RdapResolver(None).resolve("google.com")
        }
      
      case "whois" => 
        config.params.toList match {
          case domain :: Nil  =>
            new WhoisResolver().resolve(domain)
          case _ => 
            new WhoisResolver().resolve("google.com")
        }

      case "auto" => 
        config.params.toList match {
          case domain :: Nil  =>
            DnsUtil.getInfo(domain)
          case domain :: server :: Nil  =>
            DnsUtil.getInfo(domain,Some(server))
          case _ => 
            DnsUtil.getInfo("google.com")
        }
    }
    
    val r = sync(f)(DnsUtil.TIMEOUT)

    Console.err.println(s"r = ${r}")
  }
}
