package io.syspulse.skel.world

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}
import io.syspulse.skel.world.country.{CountryRegistry,CountryRoutes,CountryStoreDB}
import io.syspulse.skel.world.currency.{CurrencyRegistry,CurrencyRoutes,CurrencyStoreDB}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    val (appName,appVersion) = Util.info

    println(s"${appName}:${appVersion}")
    println(s"args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(appName), head(appName, appVersion),
        opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("port"),
        opt[String]('u', "uri").action((x, c) => c.copy(uri = x)).text("uri"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else configuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else configuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else configuration.getString("uri").getOrElse("/api/v1/world") },
        )

        println(s"config: ${config}")

        run( config.host, config.port,config.uri,configuration,
          Seq(
            (CountryRegistry(new CountryStoreDB),"CountryRegistry",(actor,actorSystem ) => new CountryRoutes(actor)(actorSystem) ),
            (CurrencyRegistry(new CurrencyStoreDB),"CurrencyRegistry",(actor,actorSystem ) => new CurrencyRoutes(actor)(actorSystem) ),
          )
        )
      }
      case _ => 
    }
  }
}

