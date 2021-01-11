package io.syspulse.auth

import io.syspulse.skeleton
import io.syspulse.skeleton.{Configuration,ConfigurationAkka,ConfigurationEnv}
import io.syspulse.auth.otp.{OtpRegistry,OtpRoutes}
import io.syspulse.auth.user.{UserRegistry,UserRoutes}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0
)

object App extends skeleton.Server {
  
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
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("host").getOrElse("localhost") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("port").getOrElse(8080) },
        )

        println(s"Config: ${config}")

        run( config.host, config.port,
          Seq(
            (OtpRegistry(),"OtpRegistry",(actor,actorSystem ) => new OtpRoutes(actor)(actorSystem) ),
            (UserRegistry(),"UserRegistry",(actor, actorSystem) => new UserRoutes(actor)(actorSystem) )
          )
        )
      }
      case _ => 
    }
  }
}

