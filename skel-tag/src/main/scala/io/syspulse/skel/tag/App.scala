package io.syspulse.skel.tag

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
import io.syspulse.skel.tag.store._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/tag",

  expr:String = "",
  
  limit:Long = -1,
  feed:String = "",
  output:String = "",
  delimiter:String = "",
  buffer:Int = 1024*1024,
  throttle:Long = 0L,

  datastore:String = "dir://store/",

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
      new ConfigurationArgs(args,"skel-tag","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('f', "feed",s"Input Feed (def: )"),
        ArgString('o', "output",s"Output file (pattern is supported: data-{yyyy-MM-dd-HH-mm}.log)"),
        ArgLong('n', "limit",s"Limit (def: ${d.limit})"),
        ArgString('_', "delimiter",s"""Delimiter characteds (def: ''). Usage example: --delimiter=`echo -e $"\r"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),

        ArgString('d', "datastore",s"Datastore [elastic://,mem,stdout,file://,dir://,resources://] (def: ${d.datastore})"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("search","Multi-Search pattern"),

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

      expr = c.getString("expr").getOrElse(d.expr),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store:TagStore = config.datastore.split("://").toList match {
      case "elastic" :: uri :: Nil => {
        val eUri = skel.uri.ElasticURI(uri)
        new TagStoreElastic(eUri.url,eUri.index)
      }
      case "mem" :: _ => new TagStoreMem()
      case "dir" :: dir :: Nil => new TagStoreDir(dir)
      case "dir" :: Nil => new TagStoreDir()
      case "file" :: file :: Nil => new TagStoreFile(file)
      case "resources" :: file :: Nil => new TagStoreResource(file)
      case "resources" :: Nil => new TagStoreResource()      
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
            (TagRegistry(store),"TagRegistry",(r, ac) => new server.TagRoutes(r)(ac) )
          )
        )
              
      // new Ingest based on Pipeline
      case "ingest" => {
        val f = new flow.PipelineTag(config.feed,config.output)(config)
        f.run()
      }

      case "search" => store.?(expr)
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