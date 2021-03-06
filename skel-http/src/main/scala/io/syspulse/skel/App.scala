package io.syspulse.skel.service

import io.syspulse.skel
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  files: Seq[String] = Seq(),
  action:String = ""
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
        opt[String]('u', "uri").action((x, c) => c.copy(uri = x)).text("uri"),

        help("help").text("simple microservice"),
        arg[String]("<file>...").unbounded().optional().action((x, c) => c.copy(files = c.files :+ x)).text("files"),
        cmd("start").action( (_, c) => c.copy(action = "start")).text("Start Command")
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else confuration.getString("uri").getOrElse("/api/v1/service") },
          files = Seq(),
          action = ""
        )

        println(s"Config: ${config}")

        run( config.host, config.port, config.uri, confuration, 
          Seq(
            (ServiceRegistry(new ServiceStoreCache),"ServiceRegistry",(actor,actorSystem ) => new ServiceRoutes(actor)(actorSystem) ),
            
          )
        )
      }
      case _ => 
    }
  }
}

