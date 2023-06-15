package io.syspulse.skel.syslog

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest.IngestFlow
import io.syspulse.skel.ingest.flow.Flows
import io.syspulse.skel.syslog.store._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/syslog",

  expr:String = "",
  
  limit:Long = -1,
  feed:String = "",
  output:String = "",
  delimiter:String = "",
  buffer:Int = 1024*1024,
  throttle:Long = 0L,

  datastore:String = "mem://",

  bus:String = "std://",
  channel:String = "sys.notify",
  scope:String = "",

  cmd:String = "server",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]): Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skell-syslog","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
                
        ArgString('f', "feed",s"Input Feed (def: ${d.feed})"),
        ArgString('o', "output",s"Output file (def: ${d.output})"),
        ArgLong('n', "limit",s"Limit (def: ${d.limit})"),
        ArgString('_', "delimiter",s"""Delimiter characteds (def: ''). Usage example: --delimiter=`echo -e $"\r"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),

        ArgString('d', "datastore",s"datastore [elastic,mem,cache] (def: ${d.datastore})"),
        ArgString('b', "bus",s"Syslog bus [kafka://,std://] (def: ${d.bus})"),
        ArgString('c', "channel",s"Syslog channel (topic) (def: ${d.channel})"),

        ArgString('_', "scope",s"Scope filter (def: ${d.scope})"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("recv","Receive Syslog Event Command"),
        ArgCmd("send","Send Syslog Event Command"),
        ArgCmd("scan","Scan all"),
        ArgCmd("search","Multi-Search pattern"),
        ArgCmd("grep","Wildcards search"),

        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      feed = c.getString("feed").getOrElse(d.feed),
      limit = c.getLong("limit").getOrElse(d.limit),
      output = c.getString("output").getOrElse(d.output),
      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),

      datastore = c.getString("datastore").getOrElse(d.datastore),
      bus = c.getString("bus").getOrElse(d.bus),
      channel = c.getString("channel").getOrElse(d.channel),
      scope = c.getString("scope").getOrElse(d.scope),

      expr = c.getString("expr").getOrElse(" "),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store:SyslogStore = config.datastore.split("://").toList match {
      case "elastic" :: uri :: Nil => new SyslogStoreElastic(uri)
      case "mem" :: Nil => new SyslogStoreMem()
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}'")
        sys.exit(1)
      }
    }
    
    val expr = config.expr + config.params.mkString(" ")

    val r = config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (SyslogRegistry(store),"SyslogRegistry",(r, ac) => new server.SyslogRoutes(r)(ac) )
          )
        )
      case "send" => 
        val (subj,msg,severity,scope) = config.params.toList match {
          case subj :: msg :: severity :: scope :: Nil => (subj,msg,Some(severity.toInt),Some(scope))
          case subj :: msg :: severity :: Nil => (subj,msg,Some(severity.toInt),Some("sys.all"))
          case subj :: msg :: Nil => (subj,msg,Some(5),Some("sys.all"))
          case subj :: Nil => (subj,"",Some(5),Some("sys.all"))
          case _ => ("Unknown","",Some(5),Some("sys.all"))
        }
        val bus = new SyslogBus("bus-1",config.bus) { override def recv(msg:SyslogEvent):SyslogEvent = msg }
        bus.send(SyslogEvent(subj,msg,severity,scope))
        
        Thread.sleep(350L)

      case "recv" => 
        val bus = new SyslogBus("bus-1",config.bus) {
          override def recv(ev:SyslogEvent):SyslogEvent = {
            println(s">>>>>>>>> event=${ev}")
            ev
          }
        }.withScope(config.scope)
        bus
      
      case "ingest" => 

      //case "get" => (new Object with DynamoGet).connect( config.elasticUri, config.elasticIndex).get(expr)
      case "scan" => store.scan(expr)
      case "search" => store.search(expr)
      case "grep" => store.grep(expr)
  
    }
    
    println(s"${"-".*(80)}\nr = ${r}")
  }
  
}