package io.syspulse.skel.odometer

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.odometer._
import io.syspulse.skel.odometer.store._
import io.syspulse.skel.odometer.server.OdoRoutes

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/odometer",
  datastore:String = "mem://",

  timeout:Long = 3000L,

  cacheFlush:Long = 5000L,
  
  cmd:String = "server",
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
      new ConfigurationArgs(args,"skel-odometer","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('d', "datastore",s"datastore [mysql,postgres,dir,mem,cache] (def: ${d.datastore})"),
        ArgString('_', "timeout",s"Timeouts, msec (def: ${d.timeout})"),

        ArgLong('_', "cache.flush",s"Cache flush interval, msec (def: ${d.cacheFlush})"),

        ArgCmd("server","Command"),
        ArgCmd("server-async","Command"),
        ArgCmd("client","Command"),
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),
      timeout = c.getLong("timeout").getOrElse(d.timeout),

      cacheFlush = c.getLong("cache.flush").getOrElse(d.cacheFlush),
     
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    config.cmd match {
      case "server" => 
        val store = config.datastore.split("://").toList match {
          case "mysql" :: db :: Nil => new OdoStoreDB(c,s"mysql://${db}")
          case "postgres" :: db :: Nil => new OdoStoreDB(c,s"postgres://${db}")
          case "mysql" :: Nil => new OdoStoreDB(c,"mysql://mysql")
          case "postgres" :: Nil => new OdoStoreDB(c,"postgres://postgres")
          case "jdbc" :: Nil => new OdoStoreDB(c,"mysql://mysql")

          case "redis" :: uri :: Nil => new OdoStoreRedis(uri)

          case "dir" :: dir ::  _ => new OdoStoreDir(dir)
          case "mem" :: Nil | "cache" :: Nil => new OdoStoreMem()
          
          case _ => {
            Console.err.println(s"Uknown datastore: '${config.datastore}'")
            sys.exit(1)
          }
        }

        // redis does not actually need a cache
        val cache = new OdoStoreCache(store, freq = config.cacheFlush)

        val reg = OdoRegistry(cache)

        run( config.host, config.port,config.uri,c,
          Seq(
            (reg,"OdoRegistry",(r, ac) => new OdoRoutes(r)(ac,config) )
          )
        )
      
      
    }
  }
}