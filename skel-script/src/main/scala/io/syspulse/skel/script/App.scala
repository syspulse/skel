package io.syspulse.skel.sentinel

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.jvm.uuid._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import java.util.Base64

import spray.json._

import io.syspulse.skel.Server
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

case class Config (
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/sentinel",
  
  datastore:String = "mem://",  
  
  feed:String = "",
  output:String = "stdout://",  
  delimiter:String = "\n", //""
  buffer:Int = 3 * 1024 * 1024, // 2MB (for gas limit 30Mgas it can be 1.9MB, so for trunk -> stdout -> sentinel pipeing it is important !)
  throttle:Long = 0L,
  throttleSource:Long = 100L,
  
  entity:String = "tx", // tx, block
  
  timeout:Long = 30000L,
  retry:Int = 3,
  env:String = "prod", // env
        
  cmd:String = "run",
  params: Seq[String] = Seq(),
)

object App extends Server {
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"sentinel","",

        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('d', "datastore",s"Datastore [mem://,gl://] (def: ${d.datastore})"),
                       
        ArgString('f', "feed",s"Input Feed (stdin://, http://, file://, kafka://) (def=${d.feed})"),
        ArgString('o', "output",s"Output (stdout://, csv://, json://, log://, file://, hive://, elastic://, kafka:// (def=${d.output})"),
        ArgString('_', "delimiter",s"""Delimiter characteds (def: '${Util.hex(d.delimiter.getBytes())}'). Usage example: --delimiter=`echo -e "\\r\\n"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),
        ArgLong('_', "throttle.source",s"Throttle source (e.g. http, def=${d.throttleSource})"),
               
        ArgString('e', "entity",s"Data entity source (entity: tx,block) (def: ${d.entity})"),

        ArgString('_', "env",s"Environment (dev/prod) (def: ${d.env})"),
        ArgLong('_', "timeout",s"Timeout (def: ${d.timeout})"),
        ArgLong('_', "retry",s"Retry (def: ${d.retry})"),

        ArgCmd("run","run"),
       
        ArgParam("<params>",""),
        ArgLogging(),
        
        ArgConfig(),
        // Must be disabled to allow arbitrary configurations for different Sentry (stored in config file)
        ArgUnknown(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),
            
      entity = c.getString("entity").getOrElse(d.entity),
      
      env = c.getString("env").getOrElse(d.env),
      timeout = c.getLong("timeout").getOrElse(d.timeout),
      retry = c.getInt("retry").getOrElse(d.retry),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    // set globla override
    
    val r = config.cmd match {      
      case "sentinel" =>
        
    }
    Console.err.println(s"r = ${r}")
  }
}
