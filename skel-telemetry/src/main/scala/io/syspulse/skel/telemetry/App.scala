package io.syspulse.skel.telemetry

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}

import akka.NotUsed
import scala.concurrent.Awaitable
import scala.concurrent.Await
import java.util.concurrent.TimeUnit
import scala.concurrent.Future

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.util.TimeUtil
import io.syspulse.skel.config._

import io.syspulse.skel.uri.DynamoURI

import io.syspulse.skel.telemetry.store._
import io.syspulse.skel.telemetry.flow.PipelineTelemetry
import io.syspulse.skel.telemetry.parser.TelemetryParserDefault
import io.syspulse.skel.telemetry.TelemetryJson._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/telemetry",

  expr:String = "",
  
  limit:Long = -1,
  feed:String = "",
  output:String = "",
  delimiter:String = "",
  buffer:Int = 1024*1024,
  throttle:Long = 0L,

  datastore:String = "dir://",
  storeCron:String = "", //"0 0/30 * * * ?", // evert 30 minutes

  cmd:String = "ingest",
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
      new ConfigurationArgs(args,"skell-telemetry","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('f', "feed",s"Input Feed (def: )"),
        ArgString('o', "output",s"Output file (pattern is supported: data-{yyyy-MM-dd-HH-mm}.log)"),

        ArgLong('n', "limit",s"Limit (def: ${d.limit})"),

        ArgString('_', "delimiter",s"""Delimiter characteds (def: ''). Usage example: --delimiter=`echo -e $"\r"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),

        ArgString('d', "datastore",s"Datastore [elastic,mem,stdout] (def: ${d.datastore})"),
        ArgString('_', "store.cron",s"Datastore Load cron (def: ${d.storeCron})"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("test","Test Command"),
        ArgCmd("scan","Scan all"),
        ArgCmd("search","Multi-Search pattern"),
        ArgCmd("grep","Wildcards search"),
        ArgCmd("typing","Typeahead"),

        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
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
      storeCron = c.getString("store.cron").getOrElse(d.storeCron),

      expr = c.getString("expr").getOrElse(d.expr),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    implicit val tp = TelemetryParserDefault

    val store:TelemetryStore = config.datastore.split("://").toList match {
      case "elastic" :: uri :: Nil => new TelemetryStoreElastic(uri)
      case "dynamo" :: uri :: Nil => new TelemetryStoreDynamo(DynamoURI(uri))
      case "mem" :: _ => new TelemetryStoreMem()
      case "stdout" :: _ => new TelemetryStoreStdout()
      case "dir" :: dir :: Nil => new TelemetryStoreDir(dir,TelemetryParserDefault, cron = Option.unless(config.storeCron.isEmpty)(config.storeCron))
      case "dir" :: Nil => new TelemetryStoreDir(parser = TelemetryParserDefault,cron = Option.unless(config.storeCron.isEmpty)(config.storeCron))
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}"')
        sys.exit(1)
      }
    }
    
    val expr = config.expr + config.params.mkString(" ")

    val r = config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (TelemetryRegistry(store),"TelemetryRegistry",(r, ac) => new server.TelemetryRoutes(r)(ac) )
          )
        )

      case "test" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (TelemetryRegistry(store),"PriceRegistry",(r, ac) => new server.PriceRoutes(r)(ac) )
          )
        )

      // new Ingest based on Pipeline
      case "ingest" => {
        val f1 = new PipelineTelemetry(config.feed,config.output)
        f1.run()
      }

      case "scan" => store.scan(expr)
      case "search" => {
        config.params.toList match {
          case ts0 :: ts1 :: _ => store.search(expr,TimeUtil.wordToTs(ts0).getOrElse(0L),TimeUtil.wordToTs(ts1).getOrElse(Long.MaxValue))
        }      
      }
    }

    r match {
      case l:List[_] => 
        Console.err.println("Results:")
        l.foreach(r => println(s"${r}"));
        sys.exit(0)
      
      case NotUsed => 
        println(r)

      //case f:Future[_] if config.cmd == "ingest" || config.cmd == "search" => Await.result(f,FiniteDuration(3000,TimeUnit.MILLISECONDS)); sys.exit(0)
      case a:Awaitable[_] if config.cmd == "ingest"  || config.cmd == "scan" => 
        Await.result(a,FiniteDuration(300000,TimeUnit.MILLISECONDS))
        sys.exit(0)

      case _ =>         
    }
    Console.err.println(s"\n${r}")     
  }
}