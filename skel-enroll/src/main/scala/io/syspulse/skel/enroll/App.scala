package io.syspulse.skel.enroll

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.crypto.Eth

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.user.UserService
import io.syspulse.skel.user.UserRegistry
import io.syspulse.skel.user.UserStoreMem
import io.syspulse.skel.user.UserRoutes

import io.syspulse.skel.enroll.store.{EnrollRegistry,EnrollStoreMem}
import io.syspulse.skel.enroll.server.EnrollRoutes

import scopt.OParser
import scala.util.Success

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = "",

  serviceUserUri:String = "",

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
      new ConfigurationArgs(args,"skel-enroll","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/enroll)"),
        ArgString('d', "datastore","datastore [mysql,postgres,mem,cache] (def: mem)"),
        
        ArgString('_', "service.user.uri","User Service URI (def: http://localhost:8080/api/v1/user)"),
        
        ArgCmd("server","Server"),
        ArgCmd("server-with-user","Server with embedded UserServices (for testing)"),
        ArgCmd("client","Http Client"),
        ArgCmd("command","Command"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/enroll"),
      datastore = c.getString("datastore").getOrElse("mem"),

      serviceUserUri = c.getString("service.user.uri").getOrElse("http://localhost:8080/api/v1/user"),

      cmd = c.getCmd().getOrElse("command"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    val store = config.datastore match {
      //case "mysql" | "db" => new EnrollStoreDB(c,"mysql")
      //case "postgres" => new EnrollStoreDB(c,"postgres")
      case "mem" | "cache" => new EnrollStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new EnrollStoreMem
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
        println(s"${Console.YELLOW}Running with EnrollService(mem):${Console.RESET} http://${host}:${config.port}${uri}/enroll")
        println(s"${Console.YELLOW}Running with UserService(mem):${Console.RESET} http://${host}:${config.port}${uri}/user")
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
        
        System.exit(0)
      }

      case "command" => {
        val r = config.params match {
          case "start" :: Nil => 
            val eid = EnrollSystem
              .withAutoTables()
              .start(
                "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
                None)

            eid          
          case "email" :: eid :: email :: Nil => 
            EnrollSystem.addEmail(UUID(eid),email)
          
          case "confirm" :: eid :: code :: Nil => 
            EnrollSystem.confirmEmail(UUID(eid),code)
          
          case "continue" :: eid :: Nil => 
            EnrollSystem.continue(UUID(eid))            

          case eid :: Nil => 
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



