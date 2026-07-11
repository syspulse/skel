package io.syspulse.skel.telemetry.ext

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import io.jvm.uuid._
import scala.concurrent.duration.FiniteDuration
import akka.actor.typed.ActorSystem

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.blockchain.Blockchain
import io.syspulse.skel.auth.jwt.AuthJwt

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit

import io.syspulse.skel.telemetry.ext.store._
import io.syspulse.skel.telemetry.ext.flow._
import io.syspulse.skel.telemetry.ext.server._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/telemetry",

  jwtUri:String = "hs512://",
  ownerAttr:String = "tenantId",
  rolesAttr:String = "groups[].",
  serviceRole:String = "extractor-service",
  adminRole:String = "extractor-admin",
  permissions:String = "user",

  expr:String = "",
  
  limit:Long = -1,
  feed:String = "stdin://",
  output:String = "stdout://",  
  delimiter:String = "\n", //""
  buffer:Int = 8192 * 100,
  throttle:Long = 1000L,
  throttleSource:Long = 100L,
  format:String = "",

  datastore:String = "mem://",
  chain:Seq[String] = Seq("ethereum"), 

  storeCron:String = "", //"0 0/30 * * * ?", // evert 30 minutes
  storeEvict:Long = 1000L * 60 * 60 * 24, // evict older than 

  threads:Int = 16, // number of threads for async operations        
  timeout:Long = 10000, // timeout for async operations
  env:String = "prod", // environment specific config

  freq:Int = 10000,

  cmd:String = "blockchain",
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
      new ConfigurationArgs(args,"telemetry-ext","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('_', "jwt.uri",s"JWT Uri [hs512://secret,rs512://pk/key] (def: ${d.jwtUri})"),
        ArgString('_', "owner.attr",s"Owner attribute in JWT (def: ${d.ownerAttr})"),
        ArgString('_', "service.role",s"Service role in JWT (def: ${d.serviceRole})"),
        ArgString('_', "admin.role",s"Admin role in JWT (def: ${d.adminRole})"),
        ArgString('_', "permissions",s"Permissions mode (def: ${d.permissions})"),
        ArgString('_', "roles.attr",s"Roles attribute in JWT (def: ${d.rolesAttr})"),
        
        ArgString('f', "feed",s"Input Feed (def: )"),
        ArgString('o', "output",s"Output file (pattern is supported: data-{yyyy-MM-dd-HH-mm}.log)"),
        ArgLong('n', "limit",s"Limit (def: ${d.limit})"),
        ArgString('_', "delimiter",s"""Delimiter characteds (def: '${Util.hex(d.delimiter.getBytes())}'). Usage example: --delimiter=`echo -e "\\r\\n"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),
        ArgLong('_', "throttle.source",s"Throttle source (e.g. http, def=${d.throttleSource})"),
        ArgString('_',"format",s"Format (def: ${d.format})"),

        ArgString('d', "datastore",s"Datastore [elastic,mem,stdout] (def: ${d.datastore})"),
        ArgString('c', "chain",s"Chains (def: ${d.chain})"),
        
        ArgString('_', "store.cron",s"Datastore Re-Load cron (def: ${d.storeCron})"),
        ArgLong('_', "store.evict",s"Datastore eviction age (def: ${d.storeEvict})"),

        ArgInt('_', "threads",s"Number of threads for async operations (def: ${d.threads})"),
        ArgString('_', "env",s"Environment (dev,prod) (def: ${d.env})"),
        ArgLong('_', "timeout",s"Timeout for async operations (def: ${d.timeout})"),

        ArgInt('_', "freq",s"Frequency for telemetry update, msec (def: ${d.freq})"),
        
        ArgCmd("server","HTTP Service"),
        ArgCmd("blockchain","Blockchain Telemetry "),
        ArgCmd("blockchain-tx","Blockchain Telemetry (Tx)"),
        ArgCmd("blockchain-block","Blockchain Block Command"),
        ArgCmd("test","Test Command"),        

        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),

      ownerAttr = c.getString("owner.attr").getOrElse(d.ownerAttr),
      serviceRole = c.getString("service.role").getOrElse(d.serviceRole).stripPrefix("'").stripSuffix("'"),
      adminRole = c.getString("admin.role").getOrElse(d.adminRole).stripPrefix("'").stripSuffix("'"),
      permissions = c.getString("permissions").getOrElse(d.permissions),
      rolesAttr = c.getString("roles.attr").getOrElse(d.rolesAttr),
      
      feed = c.getString("feed").getOrElse(d.feed),
      limit = c.getLong("limit").getOrElse(d.limit),
      output = c.getString("output").getOrElse(d.output),

      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),      
      throttleSource = c.getLong("throttle.source").getOrElse(d.throttleSource),
      format = c.getString("format").getOrElse(d.format),

      datastore = c.getString("datastore").getOrElse(d.datastore),
      chain = c.getListString("chain",d.chain),
      
      storeCron = c.getString("store.cron").getOrElse(d.storeCron),
      storeEvict = c.getLong("store.evict").getOrElse(d.storeEvict),

      expr = c.getString("expr").getOrElse(d.expr),

      threads = c.getInt("threads").getOrElse(d.threads),
      timeout = c.getLong("timeout").getOrElse(d.timeout),
      env = c.getString("env").getOrElse(d.env),

      freq = c.getInt("freq").getOrElse(d.freq),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")    

    if(! config.jwtUri.isBlank()) {
      AuthJwt(config.jwtUri)
    }
    
    val store = config.datastore.split("://").toList match {
      case "mem" :: Nil => new TelemetryExtStoreMem()
      case "dir" :: dir :: Nil => new TelemetryExtStoreDir(dir)
      case "dir" :: Nil => new TelemetryExtStoreDir()      
      case _ => 
        Console.err.println(s"Unknown datastore: '${config.datastore}'")
        sys.exit(1)      
    }

    Console.err.println(s"Store: ${store}") 

    def init():(ActorSystem[skel.Command],Seq[(Blockchain,String)]) = {
      val reg = ActorSystem(TelemetryExtRegistry(store), "TelemetryExtRegistry")

      val bf = config.chain.flatMap(chain => { 
        val blockchainFeed = chain.split("=").filter(_.nonEmpty).toList match {
          case name :: id :: feed :: Nil => Some((Blockchain(name = name, id = Some(id)), feed))
          case name :: feed :: Nil => Some((Blockchain(name = name, None), feed))
          case feed :: Nil => Some((Blockchain(name = "ethereum", None), feed))
          case _ => 
            Console.err.println(s"Invalid chain: '${chain}'")
            None
        }
        blockchainFeed
      })
      (reg, bf)
    }
    
    val r = config.cmd match {
      case "server" => // only server
        
        run( config.host, config.port,config.uri,c,
          Seq(
            (TelemetryExtRegistry(store),"TelemetryExtRegistry",(reg, ac) => {              
              new TelemetryExtRoutes(reg)(ac,config) 
            })
          )
        ) 
                
      case "test" => 
        val (reg, bf) = init()      

        bf.foreach(bf => {
          val (blockchain,feed) = bf
          val p = new PipelineBlockchain(blockchain,reg,feed,config.output)
          p.run()
        })

      case "blockchain" | "blockchain-tx" => 
        val (reg, bf) = init()      

        bf.foreach(bf => {
          val (blockchain,feed) = bf
          val p = new PipelineTelemetryBlockchainTx(blockchain,reg,feed,config.output)
          p.run()
        })

      case "blockchain-block" => 
        val (reg, bf) = init()      

        bf.foreach(bf => {
          val (blockchain,feed) = bf
          val p = new PipelineTelemetryBlockchainBlock(blockchain,reg,feed,config.output)
          p.run()
        })
        
    }
    Console.err.println(s"r = ${r}")
  }
}