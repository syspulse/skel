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

  elasticUri:String = "",
  elasticUser:String = "",
  elasticPass:String = "",
  elasticIndex:String = "",
  expr:String = "",
  
  limit:Long = -1,
  feed:String = "",
  output:String = "",
  delimiter:String = "",
  buffer:Int = 1024*1024,
  throttle:Long = 0L,

  datastore:String = "mem://",

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
        
        ArgString('_', "elastic.uri","Elastic uri (def: http://localhost:9200)"),
        ArgString('_', "elastic.user","Elastic user (def: )"),
        ArgString('_', "elastic.pass","Elastic pass (def: )"),
        ArgString('_', "elastic.index","Elastic Index (def: syslog)"),

        ArgString('f', "feed",s"Input Feed (def: )"),
        ArgString('o', "output",s"Output file (pattern is supported: data-{yyyy-MM-dd-HH-mm}.log)"),
        ArgLong('n', "limit",s"Limit (def: ${d.limit})"),
        ArgString('_', "delimiter",s"""Delimiter characteds (def: ''). Usage example: --delimiter=`echo -e $"\r"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),

        ArgString('d', "datastore","datastore [elastic,mem,cache] (def: mem)"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("scan","Scan all"),
        ArgCmd("search","Multi-Search pattern"),
        ArgCmd("grep","Wildcards search"),

        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),

      elasticUri = c.getString("elastic.uri").getOrElse("http://localhost:9200"),
      elasticIndex = c.getString("elastic.index").getOrElse("syslog"),
      elasticUser = c.getString("elastic.user").getOrElse(""),
      elasticPass = c.getString("elastic.pass").getOrElse(""),
      
      feed = c.getString("feed").getOrElse(d.feed),
      limit = c.getLong("limit").getOrElse(d.limit),
      output = c.getString("output").getOrElse(d.output),
      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),

      datastore = c.getString("datastore").getOrElse(d.datastore),

      expr = c.getString("expr").getOrElse(" "),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store:TagStore = config.datastore.split("://").toList match {
      case "elastic" => new SyslogStoreElastic(config.elasticUri,config.elasticIndex)
      case "mem" => new SyslogStoreMem()
      //case "stdout" => new SyslogStoreStdout
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}'")
        sys.exit(1)
      }
    }
    
    val expr = config.expr + config.params.mkString(" ")

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (SyslogRegistry(store),"SyslogRegistry",(r, ac) => new server.SyslogRoutes(r)(ac) )
          )
        )
      case "ingest" => new SyslogFlow()
        .connect[SyslogFlow](config.elasticUri, config.elasticIndex)
        .from(Flows.fromFile(config.feed))        
        .run()

      //case "get" => (new Object with DynamoGet).connect( config.elasticUri, config.elasticIndex).get(expr)
      case "scan" => store.scan(expr)
      case "search" => store.search(expr)
      case "grep" => store.grep(expr)
  
    }
  }
}