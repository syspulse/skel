package io.syspulse.skel.otp

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.otp.{OtpRegistry,OtpRoutes,OtpStoreDB}
import io.syspulse.skel.otp.client._

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel.otp.client.FutureAwaitable._

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = "",

  cmd:String = "",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]):Unit = {
    println(s"args: '${args.mkString(",")}'")

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"eth-stream","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/otp)"),
        ArgString('d', "datastore","datastore [mysql,postgres,mem|cache] (def: cache)"),
        ArgCmd("server","Command"),
        ArgCmd("client","Command"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/otp"),
      datastore = c.getString("datastore").getOrElse("cache"),
      cmd = c.getCmd().getOrElse("server"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    val store = config.datastore match {
      case "mysql" | "db" => new OtpStoreDB(c,"mysql")
      case "postgres" => new OtpStoreDB(c,"postgres")
      case "mem" | "cache" => new OtpStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new OtpStoreMem
      }
    }

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (OtpRegistry(store),"OtpRegistry",(a,as ) => new OtpRoutes(a)(as) ),    
          )
        )
      case "client" => {
        
        val host = if(config.host == "0.0.0.0") "localhost" else config.host
        val uri = s"http://${host}:${config.port}${config.uri}"
        val timeout = Duration("3 seconds")

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
            case "getAll" :: Nil => 
              OtpClientHttp(uri)
                .withTimeout(timeout)
                .getAll()
                .await()
            case "getForUser" :: userId :: Nil => 
              OtpClientHttp(uri)
                .withTimeout(timeout)
                .getForUser(UUID(userId))
                .await()
            case Nil => OtpClientHttp(uri)
                .withTimeout(timeout)
                .getAll()
                .await()

            case _ => println(s"unknown op: ${config.params}")
          }
        
        println(s"${r}")
        System.exit(0)
      }
    }
  }
}

