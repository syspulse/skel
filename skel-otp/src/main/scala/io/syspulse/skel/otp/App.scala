package io.syspulse.skel.otp

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.otp.{OtpRegistry,OtpRoutes,OtpStoreDB}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = "",

  files: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    println(s"args: '${args.mkString(",")}'")

    // val builder = OParser.builder[Config]
    // val argsParser = {
    //   import builder._
    //   OParser.sequence(
    //     programName(Util.info._1), head(Util.info._1, Util.info._2),
    //     opt[String]('h', "http.host").action((x, c) => c.copy(host = x)).text("listen host (def: 0.0.0.0)"),
    //     opt[Int]('p', "http.port").action((x, c) => c.copy(port = x)).text("listern port (def: 8080)"),
    //     opt[String]('u', "http.uri").action((x, c) => c.copy(uri = x)).text("rest uri (def: /api/v1/otp)"),

    //     opt[String]('d', "datastore").action((x, c) => c.copy(datastore = x)).text("datastore"),

    //     help("help").text(s"${Util.info._1} microservice"),
    //     arg[String]("...").unbounded().optional().action((x, c) => c.copy(files = c.files :+ x)).text("files"),
    //   )
    // } 
  
    // OParser.parse(argsParser, args, Config()) match {
    //   case Some(configArgs) => {
    //     val configuration = Configuration.default

    //     val config = Config(
    //       host = { if(! configArgs.host.isEmpty) configArgs.host else configuration.getString("http.host").getOrElse("0.0.0.0") },
    //       port = { if(configArgs.port!=0) configArgs.port else configuration.getInt("http.port").getOrElse(8080) },
    //       uri = { if(! configArgs.uri.isEmpty) configArgs.uri else configuration.getString("http.uri").getOrElse("/api/v1/otp") },
    //       datastore = { if(! configArgs.datastore.isEmpty) configArgs.datastore else configuration.getString("datastore").getOrElse("cache") }.toLowerCase,
    //     )

    //     println(s"Config: ${config}")

    //     val store = config.datastore match {
    //       case "mysql" | "db" => new OtpStoreDB(configuration)
    //       case "postgres" => new OtpStoreDB(configuration)
    //       case "mem" | "cache" => new OtpStoreMem
    //       case _ => {
    //         Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
    //         new OtpStoreMem
    //       }
    //     }

    //     run( config.host, config.port,config.uri,configuration,
    //       Seq(
    //         (OtpRegistry(store),"OtpRegistry",(actor,actorSystem ) => new OtpRoutes(actor)(actorSystem) ),
            
    //       )
    //     )
    //   }
    //   case _ => 
    // }

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"eth-stream","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/otp)"),
        ArgString('d', "datastore","datastore [mysql,postgres,mem|cache] (def: cache)"),
        ArgParam("<cmd>","")
      )
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/otp"),
      datastore = c.getString("datastore").getOrElse("cache"),
      files = c.getParams()
    )

    println(s"Config: ${config}")

    val store = config.datastore match {
      case "mysql" | "db" => new OtpStoreDB(c)
      case "postgres" => new OtpStoreDB(c)
      case "mem" | "cache" => new OtpStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new OtpStoreMem
      }
    }

    run( config.host, config.port,config.uri,c,
      Seq(
        (OtpRegistry(store),"OtpRegistry",(actor,actorSystem ) => new OtpRoutes(actor)(actorSystem) ),    
      )
    )
  }
}

