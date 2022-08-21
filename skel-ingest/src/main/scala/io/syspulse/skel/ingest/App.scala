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
        
        ArgString('f', "feed","Input Feed () (def: stdin://, http://, file://)"),
        ArgString('o', "output","Output sink (stdout://, file://, hive:// "),

        ArgLong('n', "limit","Limit (def: -1)"),

        ArgString('d', "datastore","Datastore [elastic,mem,stdout] (def: mem)"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/ingest"),
      
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
        val f1 = new Ingesting(config.feed,config.output)        
        f1.run()
      }     
    }

    println(s"r = ${r}")
  }
}

case class StringLike(s:String) extends skel.Ingestable {
  override def toString = s
}

class Ingesting(feed:String,output:String) extends IngestFlow[String,StringLike]() {

  def parse(data: String): Seq[String] = {
    data.split("\n").toSeq
  }
  def transform(t: String): StringLike = StringLike(s"${count}: ${t}")
  
  override def source() = {
    val source = feed.split("://").toList match {
      case "http" :: _ => IngestFlow.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = "\r")      
      case "file" :: fileName :: Nil => IngestFlow.fromFile(fileName,1024)
      case "stdin" :: _ => IngestFlow.fromStdin()
      case _ => IngestFlow.fromFile(feed)
    }
    source
  }

  override def sink() = {
    val sink = output.split("://").toList match {
      //case "http" :: _ => IngestFlow.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = "\r")      
      case "file" :: fileName :: Nil => IngestFlow.toFile(fileName)
      case "hive" :: fileName :: Nil => IngestFlow.toHiveFile(fileName)
      case "stdout" :: _ => IngestFlow.toStdout()
      case _ => IngestFlow.toFile(output)
    }
    sink
  }

}