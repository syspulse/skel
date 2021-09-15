package io.syspulse.skel.service

import io.syspulse.skel
import io.syspulse.skel.util.Util
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
    println(s"args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('h', "http.host").action((x, c) => c.copy(host = x)).text("listen host (def: 0.0.0.0)"),
        opt[Int]('p', "http.port").action((x, c) => c.copy(port = x)).text("listern port (def: 8080)"),
        opt[String]('u', "http.uri").action((x, c) => c.copy(uri = x)).text("rest uri (def: /api/v1/serivce)"),

        help("help").text("simple microservice"),
        arg[String]("<file>...").unbounded().optional().action((x, c) => c.copy(files = c.files :+ x)).text("files"),
        cmd("start").action( (_, c) => c.copy(action = "start")).text("Start Command")
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.default

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else confuration.getString("http.uri").getOrElse("/api/v1/service") },
          files = Seq(),
          action = ""
        )

        println(s"Config: ${config}")

        run( config.host, config.port, config.uri, confuration, 
          Seq(
            (ServiceRegistry(new ServiceStoreMem),"ServiceRegistry",(actor,actorSystem ) => new ServiceRoutes(actor)(actorSystem) ),
            
          )
        )
      }
      case _ => 
    }
  }
}

