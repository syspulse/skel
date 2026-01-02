package io.syspulse.dash

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.auth.jwt.AuthJwt

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import io.syspulse.dash._
import io.syspulse.dash.store._
import io.syspulse.dash.server._
import source.DataSourceCoingecko
import source.DataSourceDune
import source.DataSourceElastic
import source.DataSourceMany
import source.DataSourceTest

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/dash",

  datastore:String = "mem://",
  datasource:String = "test://",  
  timeout:Long = 15000,

  jwtUri:String = "hs512://",
  ownerAttr:String = "tenantId",
  rolesAttr:String = "groups[].",
  serviceRole:String = "extractor-service",
  adminRole:String = "extractor-admin",
  permissions:String = "user",

  threads:Int = 16, // number of threads for async operations    
  env:String = "prod", // environment specific config
  serviceUrl: String = "",
  serviceToken: String = "",  
    
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
      new ConfigurationArgs(args,"skel-dash","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"DataSource [mem://] (def: ${d.datastore})"),
        ArgString('s', "ds",s"Datasource [dune://,test://] (def: ${d.datasource})"),
        ArgLong('_', "timeout",s"RPC timeout (def: ${d.timeout})"),

        ArgString('_', "jwt.uri",s"JWT Uri [hs512://secret,rs512://pk/key] (def: ${d.jwtUri})"),
        ArgString('_', "owner.attr",s"Owner attribute in JWT (def: ${d.ownerAttr})"),
        ArgString('_', "service.role",s"Service role in JWT (def: ${d.serviceRole})"),
        ArgString('_', "admin.role",s"Admin role in JWT (def: ${d.adminRole})"),
        ArgString('_', "permissions",s"Permissions mode (def: ${d.permissions})"),
        ArgString('_', "roles.attr",s"Roles attribute in JWT (def: ${d.rolesAttr})"),

        ArgString('_', "env",s"Environment (dev,prod) (def: ${d.env})"),
        ArgInt('_', "threads",s"Number of threads for async operations (def: ${d.threads})"),
        ArgString('_', "service.url",s"Service URL (def: ${d.serviceUrl})"),
        ArgString('_', "service.token",s"Service JWT (def: ${d.serviceToken})"),
       
        
        ArgCmd("server","Server only"),
        ArgCmd("encode","Encode function"),

        ArgParam("<params>",""),
        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      jwtUri = c.getString("jwt.uri").getOrElse(d.jwtUri),

      datastore = c.getString("datastore").getOrElse(d.datastore),
      datasource = c.getString("ds").getOrElse(d.datasource).replaceAll("[\\s+]",""),

      timeout = c.getLong("timeout").getOrElse(d.timeout),

      ownerAttr = c.getString("owner.attr").getOrElse(d.ownerAttr),
      serviceRole = c.getString("service.role").getOrElse(d.serviceRole).stripPrefix("'").stripSuffix("'"),
      adminRole = c.getString("admin.role").getOrElse(d.adminRole).stripPrefix("'").stripSuffix("'"),
      permissions = c.getString("permissions").getOrElse(d.permissions),
      rolesAttr = c.getString("roles.attr").getOrElse(d.rolesAttr),
      
      env = c.getString("env").getOrElse(d.env),
      threads = c.getInt("threads").getOrElse(d.threads),
      serviceUrl = c.getString("service.url").getOrElse(""),
      serviceToken = c.getString("service.token").getOrElse(""),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),      
    )

    Console.err.println(s"Config: ${config}")


    if(! config.jwtUri.isBlank()) {
      AuthJwt(config.jwtUri)
    }

    val store = config.datastore.split("://").toList match {
      case "mem" :: Nil => new DashStoreMem()
      case "dir" :: Nil => new DashStoreDir()
      case "dir" :: dir :: Nil => new DashStoreDir(dir)

      case "postgres" :: Nil => new DashStoreDB(c,s"postgres://postgres")
      case "postgres" :: db :: Nil => new DashStoreDB(c,s"postgres://${db}")
      case "jdbc" :: db :: Nil => new DashStoreDB(c,config.datastore)
      case "jdbc" :: typ :: db :: Nil => new DashStoreDB(c,config.datastore)

      case _ => 
        Console.err.println(s"Unknown DataSource: '${config.datastore}'")
        sys.exit(1)      
    }

    val ds = config.datasource.split("://").toList match {
      
      case "dune" :: _ => new DataSourceDune(config.datasource)
      case "test" :: _ => new DataSourceTest(config.datasource)
      case ("es" | "ess" ) :: _ => new DataSourceElastic(config.datasource)
      case ("cg" | "coingecko" ) :: _ => new DataSourceCoingecko(config.datasource)
      case _ => new DataSourceMany(config.datasource)
        // Console.err.println(s"Unknown datasource: '${config.datasource}'")
        // sys.exit(2)
    }

    Console.err.println(s"Store: ${store}")
    Console.err.println(s"DataSource: ${ds}")
    
    val r = config.cmd match {
      case "server" => // only server
        run( config.host, config.port,config.uri,c,
          Seq(
            (DashRegistry(store,ds),"DashRegistry",(reg, ac) => {              
              new DashRoutes(reg)(ac,config) 
            })
          )
        )       
    }
    
    Console.err.println(s"r = ${r}")
  }
}
