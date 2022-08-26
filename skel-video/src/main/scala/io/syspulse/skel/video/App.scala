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
  host:String="",
  port:Int=0,
  uri:String = "",

  elasticUri:String = "",
  elasticUser:String = "",
  elasticPass:String = "",
  elasticIndex:String = "",
  expr:String = "",
  
  limit:Long = -1,
  feed:String = "",
  output:String = "",

  datastore:String = "",

  cmd:String = "",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]): Unit = {
    println(s"args: '${args.mkString(",")}'")

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skell-video","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/video)"),
        
        ArgString('f', "feed","Input Feed (def: )"),
        ArgString('o', "output","Output file (pattern is supported: data-{yyyy-MM-dd-HH-mm}.log)"),

        ArgString('_', "elastic.uri","Elastic uri (def: http://localhost:9200)"),
        ArgString('_', "elastic.user","Elastic user (def: )"),
        ArgString('_', "elastic.pass","Elastic pass (def: )"),
        ArgString('_', "elastic.index","Elastic Index (def: video)"),

        ArgLong('n', "limit","Limit (def: -1)"),

        ArgString('d', "datastore","Datastore [elastic,mem,stdout] (def: mem)"),
        
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
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/video"),

      elasticUri = c.getString("elastic.uri").getOrElse("http://localhost:9200"),
      elasticIndex = c.getString("elastic.index").getOrElse("video"),
      elasticUser = c.getString("elastic.user").getOrElse(""),
      elasticPass = c.getString("elastic.pass").getOrElse(""),
      
      feed = c.getString("feed").getOrElse(""),
      limit = c.getLong("limit").getOrElse(-1L),
      output = c.getString("output").getOrElse("output.log"),

      datastore = c.getString("datastore").getOrElse("mem"),

      expr = c.getString("expr").getOrElse(" "),
      
      cmd = c.getCmd().getOrElse("ingest"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    val store:VideoStore = config.datastore match {
      case "elastic" => new VideoStoreElastic(config.elasticUri,config.elasticIndex)
      case "elastic-flow" => new VideoStoreElasticFlow(config.elasticUri,config.elasticIndex)
      case "mem" => new VideoStoreMem()
      case "stdout" => new VideoStoreStdout()
      case "file" => new VideoStoreFile()
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        sys.exit(1)
      }
    }
    
    val expr = config.expr + config.params.mkString(" ")

    val r = config.cmd match {
      case "server" => 
        // run( config.host, config.port,config.uri,c,
        //   Seq(
        //     (VideoRegistry(store),"VideoRegistry",(r, ac) => new server.VideoRoutes(r)(ac) )
        //   )
        // )
        Console.err.println(s"Not supported")
        sys.exit(1)
      case "ingest" => 
        config.datastore match {
          case "elastic" =>
            // only Elastic is supported here
            new VideoStoreElasticFlow(config.elasticUri, config.elasticIndex)
              .from(Flows.fromFile(config.feed, frameSize = Int.MaxValue))
              .run()
          case "file" =>
            new VideoFlowFile(config.output)
              .from(Flows.fromFile(config.feed,frameSize = Int.MaxValue))
              .run()
          case "stdout" =>
            new VideoFlowStdout()
              .from(Flows.fromFile(config.feed,frameSize = Int.MaxValue))
              .run()
        }

      //case "get" => store.connect(config).?(expr)
      case "scan" => store.scan(expr)
      case "search" => store.search(expr)
      case "grep" => store.grep(expr)
      case "typing" => store.typing(expr)
    }

    r match {
      case l:List[_] => l.map(r => println(s"${r}"))
      case NotUsed => println(r)
      case f:Future[_] if config.cmd == "ingest" => Await.result(f,FiniteDuration(3000,TimeUnit.MILLISECONDS)); sys.exit(0)
      case a:Awaitable[_] if config.cmd == "ingest" => Await.result(a,FiniteDuration(3000,TimeUnit.MILLISECONDS)); sys.exit(0)
      case _ => Console.err.println(s"\n${r}")      
    }
    
  }
}