package io.syspulse.skel.ingest.flow

/**
 * Ingest Flow Application
 * 
 * Commands:
 *   - ingest: Single pipeline (feed -> output)
 *   - pipeline: Multiple independent pipelines (feed1 -> output1, feed2 -> output2)
 *   - flow: Chained flows using connectTo() (feed1 -> output1 => feed2 -> output2 => feed3 -> output3)
 * 
 * Flow Command Usage Examples:
 *   # Chain 2 flows: stdin -> file1 => file1 -> stdout
 *   --pipeline.flow "stdin:// => /tmp/intermediate.json => stdout://"
 *   
 *   # Chain 3 flows: file1 -> file2 => file2 -> file3 => file3 -> stdout
 *   --pipeline.flow "file:///tmp/input.json => file:///tmp/middle.json => file:///tmp/output.json => stdout://"
 *   
 *   # Chain flows with different sources/sinks
 *   --pipeline.flow "http://example.com/data => kafka://broker/topic => file:///tmp/output.json => stdout://"
 * 
 * Note: The "flow" command uses connectTo() which automatically starts upstream flows via BroadcastHub.
 * Only the last flow in the chain needs to be run explicitly.
 */

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

  actorSystem:String = "ActorSystem-IngestFlow",
  pipelineFlow:String = "stdin:// => stdout://",

  endline:String = "\n",

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
        //ArgUnknown(),
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

        ArgString('a', "actor.system",s"Actor System (def: ${d.actorSystem})"),
        ArgString('p', "pipeline.flow",s"Pipeline Flow (def: ${d.pipelineFlow})"),
        
        ArgString('_', "endline",s"Endline (def: ${d.endline})"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("ingest","Ingest Command"),        
        ArgCmd("test","Test Command"),        
        
        ArgCmd("akka-test","Akka Pipeline Command"),

        ArgCmd("pipe","Create Pipelines (Connection is opaque)"),
        ArgCmd("flow","Create Flow (Conection is transparently chained via connectTo)"),
        
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

      actorSystem = c.getString("actor.system").getOrElse(d.actorSystem),
      pipelineFlow = c.getString("pipeline.flow").getOrElse(d.pipelineFlow),

      endline = c.getString("endline").getOrElse(d.endline),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    Console.err.println(s"${c.getString("option.key1")}")

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
      
      case "test" => 
        // only for testng
        import TextlineJson._        
        val source = Flows.fromStdin().map(d => Textline(d.utf8String))
        val sink =Flows.toStdout[Textline]()
        
        implicit val system: ActorSystem = ActorSystem(config.actorSystem)
        implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
        source.runWith(sink)

      case "akka-test" => 
        implicit val system: Option[ActorSystem] = Some(ActorSystem(config.actorSystem))
        
        val pipe1 = "pipe1"
        val pipe2 = "pipe2"
        /* 
           Pipeline:
           f1(feed,"akka://1") -> f2("akka://1","akka://2") -> f3("akka://2",output)
        */

        val f3 = new PipelineTextline(
          s"akka://${config.actorSystem}/${pipe2}",
          config.output)
          // (config,system)

        f3.run()

        val f2 = new PipelineTextline(
          s"akka://${config.actorSystem}/${pipe1}",
          s"akka://${config.actorSystem}/user/${pipe2}")
          // (config,system)
        
        f2.run()
        
        //implicit val system = Some(f2.system)        
        val f1 = new PipelineTextline(
          config.feed,
          s"akka://${config.actorSystem}/user/${pipe1}")
          // (config,system)
        
        f1.run()              

      case "pipe" => 
        import TextlineJson._

        implicit val system: Option[ActorSystem] = Some(ActorSystem(config.actorSystem))

        // input:// -> output://, input:// -> output://
        val pp = config.pipelineFlow.split(",").map(_.trim).filter(!_.isBlank).map( p => {
          p.split("=>").map(_.trim).toList match {
            case feed :: output :: Nil => 
              new PipelineTextline(feed,output)(config,system)
            case _ => 
              throw new Exception(s"Invalid pipe: ${p}")
          }
        })     

        pp.foreach(f => f.run())

      case "flow" => 
        import TextlineJson._

        implicit val system: Option[ActorSystem] = Some(ActorSystem(config.actorSystem))

         val pp = config.pipelineFlow.split(",").map(_.trim).filter(!_.isBlank).map( p => {
          p.split("=>").map(_.trim).toList match {
            case feed :: output :: Nil => 
              new PipelineTextline(feed,output)(config,system)
            case _ => 
              throw new Exception(s"Invalid flow: ${p}")
          }
        }) 
                
        // Chain flows using connectTo()
        // flow1.connectTo(flow2).connectTo(flow3)...
        // connectTo returns IngestFlow[_, _, Textline], so we need to handle the type properly
        val chainedFlow: IngestFlow[_, _, Textline] = pp.tail.foldLeft[IngestFlow[_, _, Textline]](pp.head) { (prev, next) =>
          prev.connectTo(next)
        }
        
        // Only run the last flow - upstream flows are already running via BroadcastHub
        Console.err.println(s"Chaining ${pp.size} flows: ${pp.grouped(2).map(_.mkString(" -> ")).mkString(" => ")}")
        chainedFlow.run()        

    }

    Console.err.println(s"r = ${r}")
    
    //sys.exit(0)
  }
}
