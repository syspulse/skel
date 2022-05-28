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
      ).withExit(1)
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

