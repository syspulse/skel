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
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer


case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/ingest",
    
  filter:String = "",
  
  limit:Long = Long.MaxValue,
  size:Long = Long.MaxValue,

  feed:String = "stdin://",
  output:String = "stdout://",
  
  delimiter:String = "\n",
  buffer:Int = 8192 * 100,
  throttle:Long = 0L,
  throttleSource:Long = 100L,
  format:String = "",

  datastore:String = "mem",

  cmd:String = "ingest",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {

  def main(args:Array[String]): Unit = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")

    val d = Config()

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"ingest-flow","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('f', "feed",s"Input Feed (stdin://, http://, file://, kafka://) (def=${d.feed})"),
        ArgString('o', "output",s"Output (stdout://, csv://, json://, log://, file://, hive://, elastic://, kafka:// (def=${d.output})"),

        ArgString('_', "delimiter",s"""Delimiter characteds (def: '${Util.hex(d.delimiter.getBytes())}'). Usage example: --delimiter=`echo -e "\\r\\n"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),
        ArgLong('_', "throttle.source",s"Throttle source (e.g. http, def=${d.throttleSource})"),
        ArgString('_', "format",s"Format output (json,csv,log) (def=${d.format})"),

        ArgLong('n', s"limit",s"File Limit (def: ${d.limit})"),
        ArgLong('s', s"size",s"File Size Limit (def: ${d.size})"),

        ArgString('d', "datastore",s"Datastore [elastic,mem,stdout] (def: ${d.datastore})"),
                
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),
        ArgCmd("flow","Flow Command"),
        ArgCmd("akka","Flow through Akka (testing)"),
        
        ArgParam("<processors>","List of processors (none/map,print,dedup)"),
        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      feed = c.getString("feed").getOrElse(d.feed),
      output = c.getString("output").getOrElse(d.output),
      datastore = c.getString("datastore").getOrElse(d.datastore),

      limit = c.getLong("limit").getOrElse(d.limit),
      size = c.getLong("size").getOrElse(d.size),
      
      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),
      throttleSource = c.getLong("throttle.source").getOrElse(d.throttleSource),
      format = c.getString("format").getOrElse(d.format),

      filter = c.getString("filter").getOrElse(d.filter),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    // store is not used
    val store:IngestStore[_] = config.datastore match {
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
        Console.err.println(s"Not supported")
        sys.exit(1)
      case "ingest" => {
        val f1 = new PipelineTextline(config.feed,config.output)        
        f1.run()
      }
      
      case "flow" => 
        // only for testng
        import TextlineJson._        
        val source = Flows.fromStdin().map(d => Textline(d.utf8String))
        val sink =Flows.toStdout[Textline]()
        
        implicit val system: ActorSystem = ActorSystem("flow")
        implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
        source.runWith(sink)

      case "akka" => {        
        val f2 = new PipelineTextline("akka://ActorSystem-IngestFlow/flow1",config.output)        
        f2.run()
        
        // special trick to pass system to the next flow
        implicit val system = Some(f2.system)
        
        val f1 = new PipelineTextline(config.feed,"akka://ActorSystem-IngestFlow/user/flow1")
        f1.run()
        
      }
    }

    Console.err.println(s"r = ${r}")
    
    //sys.exit(0)
  }
}
