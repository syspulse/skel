package io.syspulse.skel.dns

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.jvm.uuid._

case class Config(
            
  cmd:String = "whois",
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
    
      case "whois" => 
        config.params.toList match {
          case domain :: Nil  =>
            DnsUtil.getInfo(domain)
          case domain :: whoisServer :: Nil  =>
            DnsUtil.getInfo(domain,Some(whoisServer))
          case _ => 
            DnsUtil.getInfo("google.com")
        }
        
        
        
    }
    Console.err.println(s"r = ${r}")
  }
}
