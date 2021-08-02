package io.syspulse.skel.user

import io.syspulse.skel
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}
import io.syspulse.skel.user.{UserRegistry,UserRoutes}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName("skel-user"), head("skel-user", "0.0.1"),
        opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("port"),
        opt[String]('u', "uri").action((x, c) => c.copy(uri = x)).text("uri"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else confuration.getString("uri").getOrElse("/api/v1/user") },
        )

        println(s"Config: ${config}")

        run( config.host, config.port, config.uri,
          Seq(
            (UserRegistry(),"UserRegistry",(actor, actorSystem) => new UserRoutes(actor)(actorSystem) )
          )
        )
      }
      case _ => 
    }
  }
}

