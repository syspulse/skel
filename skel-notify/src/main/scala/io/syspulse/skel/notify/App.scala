package io.syspulse.skel.notify

import scala.util.Random

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.notify._
import io.syspulse.skel.notify.client._
import io.syspulse.skel.notify.store._
import io.syspulse.skel.notify.server.NotifyRoutes
import io.syspulse.skel.notify.aws.NotifySNS

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/notify",
  datastore:String = "mem",

  cmd:String = "notify",
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
      new ConfigurationArgs(args,"skel-notify","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        ArgCmd("server",s"Server"),
        ArgCmd("client",s"Command"),
        ArgCmd("notify",s"Run notification to Receivers (email://to, stdout://, sns://arn"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store = config.datastore match {
      // case "mysql" | "db" => new NotifyStoreDB(c,"mysql")
      // case "postgres" => new NotifyStoreDB(c,"postgres")
      case "mem" | "cache" => new NotifyStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new NotifyStoreMem
      }
    }

    def parseUri(params:List[String]):(Receivers,String,String) = {
      var nn = Seq[NotifyReceiver[_]]()
      var data = Seq[String]()
      for( p <- params) {
        if(p.contains("//")) {          
          val n = p.split("://").toList match {
            case "email" :: to :: _ => new NotifyEmail(to)
            case "stdout" :: _ => new NotifyStdout
            case "sns" :: arn :: _ => new NotifySNS(arn)
            case _ => new NotifyStdout
          }
          nn = nn :+ n
        }
        else 
          data = data :+ p
      }
      val (subj,msg) = data.size match {
        case 0 => ("","")
        case 1 => ("",data(0))
        case _ => (data(0),data(1))
      } 
      (Receivers(s"group-${Util.hex(Random.nextBytes(10))}", nn),subj,msg)
    }

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (NotifyRegistry(store),"NotifyRegistry",(r, ac) => new NotifyRoutes(r)(ac) )
          )
        )
      case "notify" => 
        val (receivers,subj,msg) = parseUri(config.params.toList)
        val rr = Notification.send(receivers,subj,msg)
        Console.err.println(s"${rr}")
      
      case "client" => {
        
        // val host = if(config.host == "0.0.0.0") "localhost" else config.host
        // val uri = s"http://${host}:${config.port}${config.uri}"
        // val timeout = Duration("3 seconds")

        // val r = 
        //   config.params match {
        //     case "delete" :: id :: Nil => 
        //       NotifyClientHttp(uri)
        //         .withTimeout(timeout)
        //         .delete(UUID(id))
        //         .await()
        //     case "create" :: data => 
        //       val (email:String,name:String,eid:String) = data match {
        //         case email :: name :: eid :: _ => (email,name,eid)
        //         case email :: name :: Nil => (email,name,"")
        //         case email :: Nil => (email,"","")
        //         case Nil => ("notify-1@mail.com","","")
        //       }
        //       NotifyClientHttp(uri)
        //         .withTimeout(timeout)
        //         .create(email,name,eid)
        //         .await()
        //     case "get" :: id :: Nil => 
        //       NotifyClientHttp(uri)
        //         .withTimeout(timeout)
        //         .get(UUID(id))
        //         .await()
        //     case "getByEid" :: eid :: Nil => 
        //       NotifyClientHttp(uri)
        //         .withTimeout(timeout)
        //         .getByEid(eid)
        //         .await()
        //     case "all" :: Nil => 
        //       NotifyClientHttp(uri)
        //         .withTimeout(timeout)
        //         .all()
        //         .await()

        //     case Nil => NotifyClientHttp(uri)
        //         .withTimeout(timeout)
        //         .all()
        //         .await()

        //     case _ => println(s"unknown op: ${config.params}")
        //   }
        
        // println(s"${r}")
        // System.exit(0)
      }
    }
  }
}