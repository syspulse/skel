package io.syspulse.skel.service

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = "",
  files: Seq[String] = Seq(),
  action:String = ""
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    println(s"args: '${args.mkString(",")}'")

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-service","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/service)"),
        ArgString('d', "datastore","datastore [mysql,postgres,mem|cache] (def: cache)"),
        ArgParam("<files>","List of files"),
        ArgCmd("start","Start Command"),
        ArgHelp("help","simple microservice")        
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/service"),
      datastore = c.getString("datastore").getOrElse("cache"),
      files = c.getParams(),
      action = c.getString("action").getOrElse("start"),
    )

    println(s"Config: ${config}")

    run( config.host, config.port, config.uri, c, 
      Seq(
        (ServiceRegistry(new ServiceStoreMem),"ServiceRegistry",(actor,actorSystem ) => new ServiceRoutes(actor)(actorSystem) ),
      )
    )
  }
}

