package io.syspulse.skel.video

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
import io.syspulse.skel.config._

import io.syspulse.skel.ingest.IngestFlow
import io.syspulse.skel.ingest.flow.Flows
import io.syspulse.skel.video.store._
import io.syspulse.skel.video.file._
import io.syspulse.skel.video.elastic._


case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/video",

  elasticUri:String = "http://localhost:9200",
  elasticUser:String = "",
  elasticPass:String = "",
  elasticIndex:String = "video",
  expr:String = "",
  
  limit:Long = -1,
  feed:String = "",
  output:String = "",
  delimiter:String = "",
  buffer:Int = 1024*1024,
  throttle:Long = 0L,

  datastore:String = "mem",

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
      new ConfigurationArgs(args,"skell-video","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('f', "feed",s"Input Feed (def: )"),
        ArgString('o', "output",s"Output file (pattern is supported: data-{yyyy-MM-dd-HH-mm}.log)"),

        ArgString('_', "elastic.uri",s"Elastic uri (def: ${d.elasticUri})"),
        ArgString('_', "elastic.user",s"Elastic user (def: )"),
        ArgString('_', "elastic.pass",s"Elastic pass (def: )"),
        ArgString('_', "elastic.index",s"Elastic Index (def: ${d.elasticIndex})"),

        ArgLong('n', "limit",s"Limit (def: ${d.limit})"),

        ArgString('_', "delimiter",s"""Delimiter characteds (def: ''). Usage example: --delimiter=`echo -e $"\r"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),

        ArgString('d', "datastore",s"Datastore [elastic,mem,stdout] (def: ${d.datastore})"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("scan","Scan all"),
        ArgCmd("search","Multi-Search pattern"),
        ArgCmd("grep","Wildcards search"),
        ArgCmd("typing","Typeahead"),

        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),

      elasticUri = c.getString("elastic.uri").getOrElse(d.elasticUri),
      elasticIndex = c.getString("elastic.index").getOrElse(d.elasticIndex),
      elasticUser = c.getString("elastic.user").getOrElse(d.elasticUser),
      elasticPass = c.getString("elastic.pass").getOrElse(d.elasticPass),
      
      feed = c.getString("feed").getOrElse(d.feed),
      limit = c.getLong("limit").getOrElse(d.limit),
      output = c.getString("output").getOrElse(d.output),

      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),

      datastore = c.getString("datastore").getOrElse(d.datastore),

      expr = c.getString("expr").getOrElse(d.expr),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store:VideoStore = config.datastore.split("://").toList match {
      case "elastic" :: _ => new VideoStoreElastic(config.elasticUri,config.elasticIndex)
      case "elastic-flow" :: _ => new VideoStoreElasticFlow(config.elasticUri,config.elasticIndex)
      case "mem" :: _ => new VideoStoreMem()
      case "stdout" :: _ => new VideoStoreStdout()
      case "dir" :: dir :: Nil => new VideoStoreDir(dir)
      case "dir" :: Nil => new VideoStoreDir()
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}")
        sys.exit(1)
      }
    }
    
    val expr = config.expr + config.params.mkString(" ")

    val r = config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (VideoRegistry(store),"VideoRegistry",(r, ac) => new server.VideoRoutes(r)(ac) )
          )
        )
        
      // old Ingest based on Flows
      // case "ingest-old" => 
      //   config.datastore match {
      //     case "elastic" =>
      //       // only Elastic is supported here
      //       new VideoStoreElasticFlow(config.elasticUri, config.elasticIndex)
      //         .from(Flows.fromFile(config.feed, frameSize = Int.MaxValue))
      //         .run()
      //     case "file" =>
      //       new VideoFlowFile(config.output)
      //         .from(Flows.fromFile(config.feed,frameSize = Int.MaxValue))
      //         .run()
      //     case "stdout" =>
      //       new VideoFlowStdout()
      //         .from(Flows.fromFile(config.feed,frameSize = Int.MaxValue))
      //         .run()
      //   }

      // new Ingest based on Pipeline
      case "ingest" => {
        val f1 = new flow.PipelineVideo(config.feed,config.output)(config)
        f1.run()
      }

      case "scan" => store.scan(expr)
      case "search" => store.search(expr)
      case "grep" => store.grep(expr)
      case "typing" => store.typing(expr)
    }

    r match {
      case l:List[_] => {
        Console.err.println("Results:")
        l.foreach(r => println(s"${r}"));
        sys.exit(0)
      }
      case NotUsed => println(r)
      //case f:Future[_] if config.cmd == "ingest" || config.cmd == "search" => Await.result(f,FiniteDuration(3000,TimeUnit.MILLISECONDS)); sys.exit(0)
      case a:Awaitable[_] if config.cmd == "ingest" 
          || config.cmd == "scan"
          || config.cmd == "grep"
          || config.cmd == "typing" => Await.result(a,FiniteDuration(3000,TimeUnit.MILLISECONDS)); sys.exit(0)
      case _ => Console.err.println(s"\n${r}")      
    }
    
  }
}