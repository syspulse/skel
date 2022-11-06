package io.syspulse.skel.enroll

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.crypto.Eth

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.user.UserService
import io.syspulse.skel.user.store.UserRegistry
import io.syspulse.skel.user.store.UserStoreMem
import io.syspulse.skel.user.server.UserRoutes

import io.syspulse.skel.notify
import io.syspulse.skel.notify.NotifyService
import io.syspulse.skel.notify.store.NotifyRegistry
import io.syspulse.skel.notify.store.NotifyStoreAll
import io.syspulse.skel.notify.server.NotifyRoutes

import io.syspulse.skel.enroll.store.{EnrollRegistry,EnrollStoreMem,EnrollStoreAkka}
import io.syspulse.skel.enroll.server.EnrollRoutes

import scopt.OParser
import scala.util.Success

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/enroll",
  datastore:String = "akka",

  userUri:String = "http://localhost:8080/api/v1/user",
  notifyUri:String = "http://localhost:8080/api/v1/notify",

  notifyEmail:String = "admin@syspulse.io",

  confirmUri:String = "http://localhost:8080",

  // only for testing/demo
  smtpUri:String = "smtp://${SMTP_HOST}/${SMTP_USER}@${SMTP_PASS}",
  smtpFrom:String = "admin@domain.org",
  snsUri:String = "sns://arn:aws:sns:${AWS_REGION}:${AWS_ACCOUNT}:notify-topic",
  telegramUri:String = "tel://skel-notify/${BOT_KEY}",

  cmd:String = "command",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  import scala.concurrent.ExecutionContext.Implicits.global
  
  def main(args:Array[String]):Unit = {
    println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-enroll","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri}"),
        ArgString('d', "datastore",s"datastore (mem,akka) (def: ${d.datastore})"),
        
        ArgString('_', "user.uri",s"User Service URI (def: ${d.userUri})"),
        ArgString('_', "notify.uri",s"Notify Service URI (def: ${d.notifyUri})"),

        ArgString('_', "notify.email",s"Notification email for Enroll events (def: ${d.notifyEmail})"),
        ArgString('_', "confim.uri",s"External URI to confim email (def: ${d.confirmUri})"),
        
        ArgCmd("init","Initialize Persistnace store (create tables)"),
        ArgCmd("server","Server"),
        ArgCmd("demo","Server with embedded UserServices + NotifyService (for testing)"),
        ArgCmd("client","Http Client"),        
        ArgCmd("command","Commands list (start,email,continue,eid)"),

        ArgParam("<params>","")
      ).withExit(1)
    ))

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),

      userUri = c.getString("user.uri").getOrElse(d.userUri),
      notifyUri = c.getString("notify.uri").getOrElse(d.notifyUri),

      notifyEmail = c.getString("notify.email").getOrElse(d.notifyEmail),

      confirmUri = c.getString("confim.uri").getOrElse(d.confirmUri),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    println(s"Config: ${config}")
    
    val store = config.datastore match {
      //case "mysql" | "db" => new EnrollStoreDB(c,"mysql")
      //case "postgres" => new EnrollStoreDB(c,"postgres")
      case "mem"  => new EnrollStoreMem
      case "akka" => new EnrollStoreAkka
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}'")
        sys.exit(1)
      }
    }

    val host = if(config.host=="0.0.0.0") "localhost" else config.host
  
    config.cmd match {
      case "init" => 
        val eid = new EnrollSystem().withAutoTables()

      case "server" => 
        // initialize Tables
        new EnrollSystem().withAutoTables()

        run( config.host, config.port,config.uri,c,
          Seq(
            (EnrollRegistry(store),"EnrollRegistry",(actor, context) => {
                // discover services
                UserService.discover(config.userUri)(context.system)
                NotifyService.discover(config.notifyUri)(context.system)

                new EnrollRoutes(actor)(context, config)
              }
            )
          )
        )

      case "demo" =>
        val uri = Util.getParentUri(config.uri)
        Console.err.println(s"${Console.YELLOW}Running with EnrollService(mem):${Console.RESET} http://${host}:${config.port}${uri}/enroll")
        Console.err.println(s"${Console.YELLOW}Running with UserService(mem):${Console.RESET} http://${host}:${config.port}${uri}/user")
        Console.err.println(s"${Console.YELLOW}Running with NotifyService(mem):${Console.RESET} http://${host}:${config.port}${uri}/notify")
                
        implicit val notifyConfig = io.syspulse.skel.notify.Config(
          smtpUri = c.getString("smtp.uri").getOrElse(Configuration.withEnv(d.smtpUri)),
          smtpFrom = c.getString("smtp.from").getOrElse(d.smtpFrom),
          snsUri = c.getString("sns.uri").getOrElse(Configuration.withEnv(d.snsUri)),
          telegramUri = c.getString("telegram.uri").getOrElse(Configuration.withEnv(d.telegramUri)),
        )

        run( config.host, config.port, uri, c,
          Seq(
            (EnrollRegistry(store),"EnrollRegistry",(actor, context) => {
              // discover services
                UserService.discover(config.userUri)(context.system)
                NotifyService.discover(config.notifyUri)(context.system)
                
                new EnrollRoutes(actor)(context, config) 
              }
              .withSuffix("enroll")
            ),
            (UserRegistry(new UserStoreMem),"UserRegistry",(a, ac) => new UserRoutes(a)(ac).withSuffix("user") ),
            (NotifyRegistry(new NotifyStoreAll()),"NotifyRegistry",(a, ac) => new NotifyRoutes(a)(ac).withSuffix("notify") )
          )
        )
        
      case "client" => {
        Console.err.println(s"Client not implemented")
        System.exit(0)
      }

      case "command" => {
        Console.err.println(s"${Console.YELLOW}command:${Console.RESET} ${config.params}")
        val r = config.params match {
          case "start" :: Nil => 
            val eid = new EnrollSystem()
              .withAutoTables()
              .start(
                "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
                None)

            eid
          case "start" :: flow =>
            Console.err.println(s"${Console.YELLOW}Flow:${Console.RESET} ${flow}")
            val eid = new EnrollSystem()
              .withAutoTables()
              .start(
                flow.mkString(","),
                None)

            eid
          case "email" :: eid :: email :: Nil => 
            new EnrollSystem().addEmail(UUID(eid),email)
          
          case "confirm" :: eid :: code :: Nil => 
            new EnrollSystem().confirmEmail(UUID(eid),code)
          
          case "continue" :: eid :: Nil => 
            new EnrollSystem().continue(UUID(eid))

          case eid :: Nil => 
            // query status of the flow
            new EnrollSystem().summary(UUID(eid))            
          
          case _ => 
            val eid = new EnrollSystem().withAutoTables()
        }

        println(s"${r}")
        //System.exit(0)
      }
    }    
  }
}



