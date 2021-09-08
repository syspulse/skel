package io.syspulse.skel.otp

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}
import io.syspulse.skel.otp.{OtpRegistry,OtpRoutes,OtpStoreDB}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = ""
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("port"),
        opt[String]('u', "uri").action((x, c) => c.copy(uri = x)).text("uri"),
        opt[String]('d', "datastore").action((x, c) => c.copy(datastore = x)).text("datastore"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else confuration.getString("uri").getOrElse("/api/v1/otp") },
          datastore = { if(! configArgs.datastore.isEmpty) configArgs.datastore else confuration.getString("datastore").getOrElse("cache") }.toLowerCase,
        )

        println(s"Config: ${config}")

        val store = config.datastore match {          
          case "mysql" | "db" => new OtpStoreDB
          case "postgres" => new OtpStoreDB
          case "mem" | "cache" => new OtpStoreMem
          case _ => new OtpStoreMem
        }

        run( config.host, config.port,config.uri,confuration,
          Seq(
            (OtpRegistry(store),"OtpRegistry",(actor,actorSystem ) => new OtpRoutes(actor)(actorSystem) ),
            
          )
        )
      }
      case _ => 
    }
  }
}

