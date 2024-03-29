package io.syspulse.skel.otp

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.otp.store.{OtpRegistry,OtpStoreDB,OtpStoreMem}
import io.syspulse.skel.otp.server.{OtpRoutes}
import io.syspulse.skel.otp.client._

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/otp",
  datastore:String = "mem",

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
      new ConfigurationArgs(args,"skel-otp","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        ArgCmd("server","Command"),
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
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store = config.datastore.split("://").toList match {
      case "mysql" :: _ | "db" :: _ => new OtpStoreDB(c,"mysql")
      case "postgres" :: _ => new OtpStoreDB(c,"postgres")
      case "mem" :: _ | "cache" :: _ => new OtpStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new OtpStoreMem
      }
    }

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (OtpRegistry(store),"OtpRegistry",(a,ac) => new OtpRoutes(a)(ac) ),
          )
        )
      case "client" => {
        
        val host = if(config.host == "0.0.0.0") "localhost" else config.host
        val uri = s"http://${host}:${config.port}${config.uri}"
        val timeout = FiniteDuration(3,TimeUnit.SECONDS)

        val r = 
          config.params match {
            case "delete" :: id :: Nil => 
              OtpClientHttp(uri)
                .withTimeout(timeout)
                .delete(UUID(id))
                .await()
            case "create" :: userId :: Nil => 
              OtpClientHttp(uri)
                .withTimeout(timeout)
                .create(if(userId == "random") UUID.random else UUID(userId),"","name","account-2",None,None)
                .await()
            case "get" :: id :: Nil => 
              OtpClientHttp(uri)
                .withTimeout(timeout)
                .get(UUID(id))
                .await()
            case "all" :: Nil => 
              OtpClientHttp(uri)
                .withTimeout(timeout)
                .all()
                .await()
            case "getForUser" :: userId :: Nil => 
              OtpClientHttp(uri)
                .withTimeout(timeout)
                .getForUser(UUID(userId))
                .await()
            case Nil => OtpClientHttp(uri)
                .withTimeout(timeout)
                .all()
                .await()

            case _ => Console.err.println(s"unknown op: ${config.params}")
          }
        
        println(s"${r}")
        System.exit(0)
      }
    }
  }
}

