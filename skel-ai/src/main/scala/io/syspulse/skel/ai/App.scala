package io.syspulse.skel.ai

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.ActorSystem

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.auth.jwt.AuthJwt

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import io.syspulse.skel.ai._
import io.syspulse.skel.ai.store._
import io.syspulse.skel.ai.server._
import io.syspulse.skel.ai.source.Sources

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/ai",

  datastore:String = "openai://",
    
  feed:String = "stdin://",
  output:String = "stdout://",  
  delimiter:String = "\n", //""
  buffer:Int = 8192 * 100,
  throttle:Long = 0L,
  throttleSource:Long = 100L,
        
  cmd:String = "ask",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-ai","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [mem://,gl://,ofac://] (def: ${d.datastore})"),
               
        ArgString('f', "feed",s"Input Feed (stdin://, http://, file://, kafka://) (def=${d.feed})"),
        ArgString('o', "output",s"Output (stdout://, csv://, json://, log://, file://, hive://, elastic://, kafka:// (def=${d.output})"),
        ArgString('_', "delimiter",s"""Delimiter characteds (def: '${d.delimiter}'). Usage example: --delimiter=`echo -e $"\r\n"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),
        ArgLong('_', "throttle.source",s"Throttle source (e.g. http, def=${d.throttleSource})"),
                
        ArgCmd("server","Server"),
        ArgCmd("ask","Ask question"),
        
        ArgParam("<params>",""),
        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),
      
      feed = c.getString("feed").getOrElse(d.feed),
      output = c.getString("output").getOrElse(d.output),      
      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),
      throttleSource = c.getLong("throttle.source").getOrElse(d.throttleSource),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    val store = config.datastore.split("://").toList match {
      case "openai" :: Nil => new AiStoreOpenAi(config.datastore)
      case "openai" :: uri :: Nil => new AiStoreOpenAi(uri)      
      // case "claude" :: Nil => new AiStoreClaude()
      // case "claude" :: dir :: _ => new AiStoreClaude(dir)
      case "mem" :: _ => new AiStoreMem()
      case _ => 
        Console.err.println(s"Unknown datastore: '${config.datastore}'")
        sys.exit(1)      
    }

    Console.err.println(s"Store: ${store}")
    
    val r = config.cmd match {
      case "server" =>
        
        run( config.host, config.port,config.uri,c,
          Seq(
            (AiRegistry(store),"AiRegistry",(reg, ac) => {              
              new AiRoutes(reg)(ac,config) 
            })
          )
        ) 

      case "ask" => 
        config.params.toList match {
          case file :: _ if(file.startsWith("file://")) =>
            val text = os.read(os.Path(file.stripPrefix("file://"),os.pwd))
            store.????(text,None,Some(Sources.OPEN_AI))
          case _ => 
            store.????(config.params.mkString(" "),None,Some(Sources.OPEN_AI))
        }
        
        
        
    }
    Console.err.println(s"r = ${r}")
  }
}
