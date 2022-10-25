package io.syspulse.skel.notify

import scala.util.Random

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.scaladsl.Behaviors

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.notify._
import io.syspulse.skel.notify.client._
import io.syspulse.skel.notify.store._
import io.syspulse.skel.notify.server.NotifyRoutes
import io.syspulse.skel.notify.server.WsNotifyRoutes

import io.syspulse.skel.notify.aws.NotifySNS
import io.syspulse.skel.notify.email.NotifyEmail
import io.syspulse.skel.notify.ws.NotifyWebsocket
import io.syspulse.skel.notify.telegram.NotifyTelegram

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/notify",
  datastore:String = "all",

  smtpUri:String = "smtp://smtp.gmail.com:587/user@pass",
  smtpFrom:String = "admin@syspulse.io",

  telegramUri:String = "tel://skel-notify/$BOT_KEY",

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
        ArgString('d', "datastore",s"datastore [all] (def: ${d.datastore})"),

        ArgString('_', "smtp.uri",s"STMP uri (def: ${d.smtpUri})"),
        ArgString('_', "smtp.from",s"From who to send to (def: ${d.smtpFrom})"),

        ArgString('_', "telegram.uri",s"Telegram uri (def: ${d.telegramUri})"),
        
        ArgCmd("server",s"Server"),
        ArgCmd("client",s"Command"),
        ArgCmd("notify",s"Run notification to Receivers (smtp://to, stdout://, sns://arn, ws://topic, tel://)"),
        ArgCmd("server+notify",s"Server + Notify"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),

      smtpUri = c.getString("smtp.uri").getOrElse(d.smtpUri),
      smtpFrom = c.getString("smtp.from").getOrElse(d.smtpFrom),

      telegramUri = c.getString("telegram.uri").getOrElse(d.telegramUri),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store = config.datastore match {
      // case "mysql" | "db" => new NotifyStoreDB(c,"mysql")
      // case "postgres" => new NotifyStoreDB(c,"postgres")
      case "all" => new NotifyStoreAll
      case _ => {
        Console.err.println(s"Unknown datastore: '${config.datastore}': using 'all'")
        new NotifyStoreAll
      }
    }

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (Behaviors.ignore,"",(actor,actorSystem) => new WsNotifyRoutes()(actorSystem) ),
            (NotifyRegistry(store),"NotifyRegistry",(r, ac) => new NotifyRoutes(r)(ac) )
          )
        )
      case "notify" => 
        val (receivers,subj,msg) = Notification.parseUri(config.params.toList)
        val rr = Notification.send(receivers,subj,msg)
        Console.err.println(s"${rr}")

      case "server+notify" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (Behaviors.ignore,"",(actor,actorSystem) => new WsNotifyRoutes()(actorSystem) ),
            (NotifyRegistry(store),"NotifyRegistry",(r, ac) => new NotifyRoutes(r)(ac) )
          )
        )
        
        while(true) {
          try {
            val (receivers,subj,msg) = Notification.parseUri(scala.io.StdIn.readLine().split("\\s+").toList)
            val rr = Notification.send(receivers,subj,msg)
            Console.err.println(s"${rr}")
          } catch {
            case e:Exception => sys.exit(1)
          }
        }
      
      case "client" => {        
        import io.syspulse.skel.FutureAwaitable._
        
        val host = if(config.host == "0.0.0.0") "localhost" else config.host
        val uri = s"http://${host}:${config.port}${config.uri}"
        val timeout = Duration("3 seconds")

        val r = 
          config.params match {
            case "notify" :: data => 
              val (to:String,subj:String,msg:String) = data match {                
                case to :: subj :: msg :: Nil => (to,subj,msg)
                case to :: subj :: Nil => (to,subj,"")
                case to :: Nil => (to,"","")
                case Nil => ("stdout://","","")
                case _ => (data.take(data.size-2).mkString(" "),data.takeRight(2).head,data.last)
              }
              Console.err.println(s"(${to},${subj},${msg}) -> ${uri}")
              NotifyClientHttp(uri)
                .withTimeout(timeout)
                .notify(to,subj,msg)
                .await()
                      
            case _ => println(s"unknown op: ${config.params}")
          }
        
        println(s"${r}")
        sys.exit(0)
      }
    }
  }
}