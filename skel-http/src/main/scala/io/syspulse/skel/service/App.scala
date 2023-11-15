package io.syspulse.skel.service

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.service.ws._

import scopt.OParser
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/service",
  datastore:String = "mem",

  cmd:String = "server",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    println(s"args: '${args.mkString(",")}'")

        val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-service","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        ArgCmd("server","Command"),
        ArgCmd("client","Command"),
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    @volatile
    var ws:Option[WsServiceRoutes] = None

    val r = run( config.host, config.port, config.uri, c, 
      Seq(
        // example of WebSocker processor
        (Behaviors.ignore,"",(actor,actorSystem) => {
          ws = Some(new WsServiceRoutes()(actorSystem))
          ws.get
        }),
        (ServiceRegistry(new ServiceStoreMem),"ServiceRegistry",(actor,actorSystem ) => new ServiceRoutes(actor)(actorSystem) ),
      )
    )
    
    while(true) {
      try {
        val d  = scala.io.StdIn.readLine().split("\\s+").toList
        d match {
          case txt :: Nil => ws.get.broadcast(txt)
          case txt :: topic :: Nil => ws.get.broadcast(txt,topic)
          case _ => ws.get.broadcast(d.mkString(" "))
        }
        
      } catch {
        case e:Exception => sys.exit(1)
      }
    }

    Console.err.println(s"r=${r}")
  }
}

