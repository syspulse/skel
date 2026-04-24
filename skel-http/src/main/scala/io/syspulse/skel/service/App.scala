package io.syspulse.skel.service

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.service.ws._

import scopt.OParser
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.HTTP

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/service",
  datastore:String = "mem",

  timeout:Long = 3000,

  headers: Map[String, String] = Map(),

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
      new ConfigurationArgs(args,"skel-http","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        ArgString('_', "timeout",s"Timeouts, msec (def: ${d.timeout})"),

        ArgString('_', "headers",s"Headers, key=value (def: ${d.headers.mkString(",")})"),

        ArgCmd("server","Command"),
        ArgCmd("client","Command"),
        ArgCmd("http","HTTP"),

        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),

      timeout = c.getLong("timeout").getOrElse(d.timeout),
      headers = c.getMap("headers",d.headers),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val r = config.cmd match {
      case "server" => 
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

      case "http" => 
        val f = config.params.toList match {
          case "get" :: url :: Nil => HTTP.get(url, None, config.headers.toSeq, config.timeout)
          case "get" :: url :: body :: Nil => HTTP.get(url, Some(body.mkString("\n")), config.headers.toSeq, config.timeout)
          case "post" :: url :: Nil => HTTP.post(url, None, config.headers.toSeq, config.timeout)
          case "post" :: url :: body => HTTP.post(url, Some(body.mkString("\n")), config.headers.toSeq, config.timeout)
          case "put" :: url :: Nil => HTTP.put(url, None, config.headers.toSeq, config.timeout)
          case "put" :: url :: body :: Nil => HTTP.put(url, Some(body.mkString("\n")), config.headers.toSeq, config.timeout)
          case "delete" :: url :: Nil => HTTP.delete(url, None, config.headers.toSeq, config.timeout)
          case "delete" :: url :: body :: Nil => HTTP.delete(url, Some(body.mkString("\n")), config.headers.toSeq, config.timeout)
          case _ => 
            Console.err.println(s"Unknown verb: ${config.params.mkString(",")}")
            sys.exit(1)
        }
        HTTP.await(f,config.timeout)

      case _ => 
        Console.err.println(s"Unknown command: ${config.cmd}")
        sys.exit(1)
      
    }

    Console.err.println(s"r=${r}")
  }
}

