package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest.store._
import akka.util.ByteString
import akka.http.javadsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",

  elasticUri:String = "",
  elasticUser:String = "",
  elasticPass:String = "",
  elasticIndex:String = "",
  
  filter:String = "",
  
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
      new ConfigurationArgs(args,"skell-ingest","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/ingest)"),
        
        ArgString('f', "feed","Input Feed (def: )"),
        ArgString('o', "output","Output file (pattern is supported: data-{yyyy-MM-dd-HH-mm}.log)"),

        ArgString('_', "elastic.uri","Elastic uri (def: http://localhost:9200)"),
        ArgString('_', "elastic.user","Elastic user (def: )"),
        ArgString('_', "elastic.pass","Elastic pass (def: )"),
        ArgString('_', "elastic.index","Elastic Index (def: ingest)"),

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
      uri = c.getString("http.uri").getOrElse("/api/v1/ingest"),

      elasticUri = c.getString("elastic.uri").getOrElse("http://localhost:9200"),
      elasticIndex = c.getString("elastic.index").getOrElse("ingest"),
      elasticUser = c.getString("elastic.user").getOrElse(""),
      elasticPass = c.getString("elastic.pass").getOrElse(""),
      
      feed = c.getString("feed").getOrElse("stdin://"),
      datastore = c.getString("datastore").getOrElse("mem"),

      limit = c.getLong("limit").getOrElse(-1L),
      output = c.getString("output").getOrElse("output.log"),

      filter = c.getString("filter").getOrElse(""),
      
      cmd = c.getCmd().getOrElse("ingest"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    val store:IngestStore[_,_] = config.datastore match {
      //case "elastic" => new StoreElastic().connect(config)
      case "mem" => new IngestStoreMem()
      case "stdout" => new IngestStoreStdout()
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}")
        sys.exit(1)
      }
    }
    
    val filter = config.filter + config.params.mkString(" ")

    val r = config.cmd match {
      case "server" => 
        // run( config.host, config.port,config.uri,c,
        //   Seq(
        //     (VideoRegistry(store),"VideoRegistry",(r, ac) => new server.VideoRoutes(r)(ac) )
        //   )
        // )
        Console.err.println(s"Not supported")
        sys.exit(1)
      case "ingest" => {
        val f1 = new Ingesting(config.feed)
        
        f1.run()
        // config.datastore match {
        //   case "elastic" => 
        //     new IngestFlowElastic()
        //       .connect[IngestFlowElastic](config.elasticUri, config.elasticIndex)
        //       .from(IngestFlow.fromFile(config.feed))
        //       .run()
        //   case "mem" | "stdout" => 
        //     new VideoFlowFile(config.output)
        //       .from(IngestFlow.fromFile(config.feed))
        //       .run()
        }

      //case "get" => store.connect(config).?(filter)
      // case "scan" => store.connect(config).scan(filter)
      // case "search" => store.connect(config).search(filter)
      // case "grep" => store.connect( config).grep(filter)
      // case "typing" => store.connect( config).typing(filter)
    }

    println(s"r = ${r}")
  }
}

class Ingesting(feed:String) extends IngestFlow[String,String]() {
  def parse(data: String): Seq[String] = {
    data.split("\n").toSeq
  }
  def transform(t: String): String = s"${count}: ${t}"
  
  override def source() = {
    val flow = feed.split("://").toList match {
      case "http" :: _ => IngestFlow.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = "\r")      
      case "file" :: fileName :: Nil => IngestFlow.fromFile(fileName,1024)
      case "stdin" :: _ => IngestFlow.fromStdin()
      case _ => IngestFlow.fromFile(feed)
    }
    flow
  }
}