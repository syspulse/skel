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

import io.syspulse.skel.enroll.store.{EnrollRegistry,EnrollStoreMem,EnrollStoreAkka}
import io.syspulse.skel.enroll.server.EnrollRoutes

import scopt.OParser
import scala.util.Success

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/enroll",
  datastore:String = "akka",

  serviceUserUri:String = "http://localhost:8080/api/v1/user",

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
        ArgString('u', "http.uri",s"api uri (def: /api/v1/enroll)"),
        ArgString('d', "datastore",s"datastore (mem,akka) (def: ${d.datastore})"),
        
        ArgString('_', "service.user.uri",s"User Service URI (def: ${d.serviceUserUri})"),
        
        ArgCmd("server","Server"),
        ArgCmd("server-with-user","Server with embedded UserServices (for testing)"),
        ArgCmd("client","Http Client"),        
        ArgCmd("command","Commands list (start,email,continue,eid)"),

        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),

      serviceUserUri = c.getString("service.user.uri").getOrElse(d.serviceUserUri),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    println(s"Config: ${config}")
    
    val store = config.datastore match {
      //case "mysql" | "db" => new EnrollStoreDB(c,"mysql")
      //case "postgres" => new EnrollStoreDB(c,"postgres")
      case "mem" | "cache" => new EnrollStoreMem
      case "akka" => new EnrollStoreAkka
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}'")
        sys.exit(1)
      }
    }

    val host = if(config.host=="0.0.0.0") "localhost" else config.host
  
    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (EnrollRegistry(store),"EnrollRegistry",(actor, context) => {
                // initialize UserService
                UserService.discover(config.serviceUserUri)(context.system)

                new EnrollRoutes(actor)(context, config)
              }
            )            
          )
        )

      case "server-with-user" =>
        val uri = Util.getParentUri(config.uri)
        Console.err.println(s"${Console.YELLOW}Running with EnrollService(mem):${Console.RESET} http://${host}:${config.port}${uri}/enroll")
        Console.err.println(s"${Console.YELLOW}Running with UserService(mem):${Console.RESET} http://${host}:${config.port}${uri}/user")
        run( config.host, config.port, uri, c,
          Seq(
            (EnrollRegistry(store),"EnrollRegistry",(actor, context) => {
                UserService.discover(config.serviceUserUri)(context.system)
                new EnrollRoutes(actor)(context, config) 
              }
              .withSuffix("enroll")
            ),
            (UserRegistry(new UserStoreMem),"UserRegistry",(a, ac) => new UserRoutes(a)(ac).withSuffix("user") )
            
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
            val eid = EnrollSystem
              .withAutoTables()
              .start(
                "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
                None)

            eid
          case "start" :: flow =>
            Console.err.println(s"${Console.YELLOW}Flow:${Console.RESET} ${flow}")
            val eid = EnrollSystem
              .withAutoTables()
              .start(
                flow.mkString(","),
                None)

            eid
          case "email" :: eid :: email :: Nil => 
            EnrollSystem.addEmail(UUID(eid),email)
          
          case "confirm" :: eid :: code :: Nil => 
            EnrollSystem.confirmEmail(UUID(eid),code)
          
          case "continue" :: eid :: Nil => 
            EnrollSystem.continue(UUID(eid))            

          case eid :: Nil => 
            // query status of the flow
            EnrollSystem.summary(UUID(eid))            

          case _ => 
            val eid = EnrollSystem.withAutoTables()
        }

        println(s"${r}")
        //System.exit(0)
      }
    }    
  }
}



