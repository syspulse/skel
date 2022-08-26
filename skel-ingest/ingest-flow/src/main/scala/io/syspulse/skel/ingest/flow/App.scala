package io.syspulse.skel.ingest.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import akka.util.ByteString
import akka.http.javadsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest._
import io.syspulse.skel.ingest.store._


case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
    
  filter:String = "",
  
  limit:Long = -1,
  feed:String = "",
  output:String = "",
  
  delimiter:String = "",
  buffer:Long = 0L,

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
      new ConfigurationArgs(args,"ingest-flow","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/ingest)"),
        
        ArgString('f', "feed","Input Feed () (def: stdin://, http://, file://)"),
        ArgString('o', "output","Output sink (stdout://, file://, hive:// "),

        ArgString('_', "delimiter","""Delimiter characteds (def: '\n'). Usage example: --delimiter=`echo -e $"\r"` """),
        ArgLong('_', "buffer","Frame buffer (Akka Framing) (def: 8192)"),

        ArgLong('n', "limit","Limit (def: -1)"),

        ArgString('d', "datastore","Datastore [elastic,mem,stdout] (def: mem)"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        
        ArgParam("<params>","")
      ).withExit(1)
    ))

    implicit val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/ingest"),
      
      feed = c.getString("feed").getOrElse("stdin://"),
      datastore = c.getString("datastore").getOrElse("mem"),

      limit = c.getLong("limit").getOrElse(-1L),
      output = c.getString("output").getOrElse("output.log"),

      delimiter = c.getString("delimiter").getOrElse("\n"),
      buffer = c.getLong("buffer").getOrElse(8192),

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



import spray.json._
import DefaultJsonProtocol._
object StringLikeJson extends  DefaultJsonProtocol {
  implicit val fmt = jsonFormat1(StringLike.apply _)
}

case class StringLike(s:String) extends skel.Ingestable {
  override def toString = s
}

class Ingesting(feed:String,output:String)(implicit config:Config) extends IngestFlow[String,String,StringLike]() {

  def flow:Flow[String,String,_] = Flow[String].map(s => s)
  
  def parse(data: String): Seq[String] = {
    data.split("\n").toSeq
  }
  def transform(t: String): Seq[StringLike] = Seq(StringLike(s"${count}: ${t}"))
  
  override def source() = {
    val source = feed.split("://").toList match {
      case "http" :: _ => Flows.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = config.delimiter,frameSize = config.buffer.toInt)
      case "https" :: _ => Flows.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = config.delimiter,frameSize = config.buffer.toInt)
      case "file" :: fileName :: Nil => Flows.fromFile(fileName,1024,frameDelimiter = config.delimiter, frameSize = config.buffer.toInt)
      case "stdin" :: _ => Flows.fromStdin()
      case _ => Flows.fromFile(feed,1024,frameDelimiter = config.delimiter,frameSize = config.buffer.toInt)
    }
    source
  }

  override def sink() = {
    import StringLikeJson._
    val sink = output.split("://").toList match {
      case "elastic" :: _ => Flows.toElastic[StringLike](output)
      case "file" :: fileName :: Nil => Flows.toFile(fileName)
      case "hive" :: fileName :: Nil => Flows.toHiveFile(fileName)
      case "stdout" :: _ => Flows.toStdout()
      case _ => Flows.toFile(output)
    }
    sink
  }

}