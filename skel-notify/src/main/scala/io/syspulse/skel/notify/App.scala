package io.syspulse.skel.notify

import scala.util.Random

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

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

import io.syspulse.skel.notify.store._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/notify",
  datastore:String = "mem://",

  smtpUri:String = "smtp://${SMTP_HOST}/${SMTP_USER}/${SMTP_PASS}",
  smtpFrom:String = "admin@syspulse.io",

  snsUri:String = "sns://arn:aws:sns:${AWS_REGION}:${AWS_ACCOUNT}:notify-topic",
  
  telegramUri:String = "tel://skel-notify/${BOT_KEY}",

  timeout:Long = 10000L,
  timeoutIdle:Long = 1000L*60*10,

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
        ArgString('d', "datastore",s"datastore [mem://,dir://] (def: ${d.datastore})"),

        ArgString('_', "smtp.uri",s"STMP uri (def: ${d.smtpUri})"),
        ArgString('_', "smtp.from",s"From who to send to (def: ${d.smtpFrom})"),

        ArgString('_', "sns.uri",s"SNS uri (def: ${d.snsUri})"),

        ArgString('_', "telegram.uri",s"Telegram uri (def: ${d.telegramUri})"),

        ArgLong('_', "timeout",s"timeout (msec, def: ${d.timeout})"),
        ArgLong('_', "timeout.idle",s"timeout for Idle WS connection (msec, def: ${d.timeoutIdle})"),
        
        ArgCmd("server",s"Server"),
        ArgCmd("client",s"Command"),
        ArgCmd("notify",s"Run notification to Receivers (smtp://to, stdout://, sns://arn, ws://topic, tel://, kafka://, http://)"),
        ArgCmd("server+notify",s"Server + Notify"),
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),

      smtpUri = c.getString("smtp.uri").getOrElse(Configuration.withEnv(d.smtpUri)),
      smtpFrom = c.getString("smtp.from").getOrElse(d.smtpFrom),

      snsUri = c.getString("sns.uri").getOrElse(Configuration.withEnv(d.snsUri)),

      telegramUri = c.getString("telegram.uri").getOrElse(Configuration.withEnv(d.telegramUri)),

      timeout = c.getLong("timeout").getOrElse(d.timeout),
      timeoutIdle = c.getLong("timeout.idle").getOrElse(d.timeoutIdle),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store = config.datastore.split("://").toList match {
      case "dir" :: Nil => new NotifyStoreDir
      case "dir" :: dir :: Nil => new NotifyStoreDir(dir)
      case "mem" :: Nil => new NotifyStoreMem
      case _ => {
        Console.err.println(s"Unknown datastore: '${config.datastore}'")
        sys.exit(1)
      }
    }

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (Behaviors.ignore,"WS-Notify",(actor,actorSystem) => new WsNotifyRoutes(config.timeoutIdle)(actorSystem) ),
            (Behaviors.ignore,"WS-Users",(actor,actorSystem) => new WsNotifyRoutes(config.timeoutIdle,"user")(actorSystem) ),
            (NotifyRegistry(store),"NotifyRegistry",(r, ac) => new NotifyRoutes(r)(ac) )
          )
        )
      case "notify" => 
        val (receivers,subj,msg) = Notification.parseUri(config.params.toList)
        val rr = Notification.broadcast(receivers.receviers,subj,msg,Some(2),Some("sys.all"))
        Console.err.println(s"${rr}")

      case "server+notify" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (Behaviors.ignore,"WS-Notify",(actor,actorSystem) => new WsNotifyRoutes(config.timeoutIdle)(actorSystem) ),
            (Behaviors.ignore,"WS-Users",(actor,actorSystem) => new WsNotifyRoutes(config.timeoutIdle,"user")(actorSystem) ),
            (NotifyRegistry(store),"NotifyRegistry",(r, ac) => new NotifyRoutes(r)(ac) )
          )
        )
        
        while(true) {
          try {
            val (receivers,subj,msg) = Notification.parseUri(scala.io.StdIn.readLine().split("\\s+").toList)
            val rr = Notification.broadcast(receivers.receviers,subj,msg,Some(1),Some("sys.all"))
            Console.err.println(s"${rr}")
          } catch {
            case e:Exception => sys.exit(1)
          }
        }
      
      case "client" => {        
        import io.syspulse.skel.FutureAwaitable._
        
        val host = if(config.host == "0.0.0.0") "localhost" else config.host
        val uri = s"http://${host}:${config.port}${config.uri}"
        val timeout = FiniteDuration(10,TimeUnit.SECONDS)

        val r = 
          config.params match {
            case "notify" :: data => 
              val (to:String,subj:String,msg:String,severity:Int,scope:String) = data match {
                case to :: subj :: msg :: severity :: scope :: Nil => (to,subj,msg,severity.toInt,scope)
                case to :: subj :: msg :: severity :: Nil => (to,subj,msg,severity.toInt,"sys.all")
                case to :: subj :: msg :: Nil => (to,subj,msg,1,"sys.all")
                case to :: subj :: Nil => (to,subj,"",1,"sys.all")
                case to :: Nil => (to,"","",1,"sys.all")
                case Nil => ("stdout://","","",1,"sys.all")
                case _ => (data.take(data.size-2).mkString(" "),data.takeRight(2).head,data.last)
              }
              Console.err.println(s"(${to},${subj},${msg}) -> ${uri}")
              NotifyClientHttp(uri)
                .withTimeout(timeout)
                .notify(to,subj,msg,Some(severity),Some(scope))
                .await()
                      
            case _ => println(s"unknown op: ${config.params}")
          }
        
        println(s"${r}")
        sys.exit(0)
      }
    }
  }
}