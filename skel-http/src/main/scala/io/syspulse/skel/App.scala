package io.syspulse.auth

import io.syspulse.skel
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}
import io.syspulse.skel.otp.{OtpRegistry,OtpRoutes,OtpStoreDB}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName("skel-http"), head("skel-http", "0.0.1"),
        opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("port"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("port").getOrElse(8080) },
        )

        println(s"Config: ${config}")

        run( config.host, config.port,
          Seq(
            (OtpRegistry(new OtpStoreDB),"OtpRegistry",(actor,actorSystem ) => new OtpRoutes(actor)(actorSystem) ),
            
          )
        )
      }
      case _ => 
    }
  }
}

