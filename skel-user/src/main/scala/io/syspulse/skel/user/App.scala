package io.syspulse.skel.user

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.user._
import io.syspulse.skel.user.client._
import io.syspulse.skel.user.store._
import io.syspulse.skel.user.server.UserRoutes
import io.syspulse.skel.uri.JdbcURI

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/user",
  datastore:String = "mem://",

  timeout:Long = 3000L,

  uploadStore: String = "/tmp/upload",
  uploadUri: String = "http://localhost:8080/upload",

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
      new ConfigurationArgs(args,"skel-user","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [mysql,postgres,dir,mem,cache] (def: ${d.datastore})"),
        ArgString('_', "timeout",s"Timeouts, msec (def: ${d.timeout})"),

        ArgString('_', "upload.store",s"Store for user uploaded files (def: ${d.uploadStore})"),
        ArgString('_', "upload.uri",s"Uri for uploaded data (def: ${d.uploadUri})"),

        ArgCmd("server","Command"),
        ArgCmd("server-async","Command"),
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
      timeout = c.getLong("timeout").getOrElse(d.timeout),

      uploadStore = c.getString("upload.store").getOrElse(d.uploadStore),
      uploadUri = c.getString("upload.uri").getOrElse(d.uploadUri),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    config.cmd match {
      case "server" => 
        val store:UserStore = config.datastore.split("://|/").toList match {

          case "dir" :: dir ::  _ => new UserStoreDir(dir)
          case "mem" :: Nil | "cache" :: Nil => new UserStoreMem()

          // case "mysql" :: db :: Nil => (Some(new UserStoreDB(c,s"mysql://${db}")),None)
          // case "postgres" :: db :: Nil => (Some(new UserStoreDB(c,s"postgres://${db}")),None)
          // case "mysql" :: Nil => (Some(new UserStoreDB(c,"mysql://mysql")),None)
          // case "postgres" :: Nil => (Some(new UserStoreDB(c,"postgres://postgres")),None)

          // case "jdbc" :: Nil => (Some(new UserStoreDB(c,"mysql://mysql")),None)
          // case "jdbc" :: "async" :: Nil => (None,Some(new UserStoreDBAsync(c,s"mysql://mysql")))
          // case "jdbc" :: typ :: Nil => (Some(new UserStoreDB(c,s"${typ}://${typ}")),None)
          
          // case "jdbc" :: "async" :: typ :: Nil => (None,Some(new UserStoreDBAsync(c,s"${typ}://${typ}")))
          // case "jdbc" :: "async" :: typ:: db :: Nil => (None,Some(new UserStoreDBAsync(c,s"${typ}://${db}")))          
          // case "jdbc" :: typ :: db :: Nil => (Some(new UserStoreDB(c,s"${typ}://${db}")),None)

          case _ => 
            val uri = new JdbcURI(config.datastore)
            if(uri.async) new UserStoreDBAsync(c,config.datastore) else new UserStoreDB(c,config.datastore)
                      
          // case _ => {
          //   Console.err.println(s"Uknown datastore: '${config.datastore}'")
          //   sys.exit(1)
          // }
        }

        val reg = if(store.isInstanceOf[UserStoreDB]) UserRegistry(store.asInstanceOf[UserStoreDB]) else UserRegistryAsync(store.asInstanceOf[UserStoreDBAsync])

        run( config.host, config.port,config.uri,c,
          Seq(
            (reg,"UserRegistry",(r, ac) => new UserRoutes(r)(ac,config) )
          )
        )
      
      case "server-async" => 
        val store = config.datastore.split("://").toList match {
          case "mysql" :: _ => new UserStoreDBAsync(c,"mysql_async")
          case "postgres" :: _ => new UserStoreDBAsync(c,"postgres_async")
          case _ => {
            Console.err.println(s"Uknown datastore: '${config.datastore}'")
            sys.exit(1)
          }
        }

        run( config.host, config.port,config.uri,c,
          Seq(
            (UserRegistryAsync(store),"UserRegistry",(r, ac) => new UserRoutes(r)(ac,config) )
          )
        )
      case "client" => {
        
        val host = if(config.host == "0.0.0.0") "localhost" else config.host
        val uri = s"http://${host}:${config.port}${config.uri}"
        val timeout = FiniteDuration(config.timeout,TimeUnit.MILLISECONDS)

        val r = 
          config.params match {
            case "delete" :: id :: Nil => 
              UserClientHttp(uri)
                .withTimeout(timeout)
                .delete(UUID(id))
                .await()
            case "create" :: data => 
              val (email:String,name:String,xid:String,avatar:String) = data match {
                case email :: name :: xid :: avatar :: _ => (email,name,xid,avatar)
                case email :: name :: xid :: Nil => (email,name,xid,"")
                case email :: name :: Nil => (email,name,"","")
                case email :: Nil => (email,"","","")
                case Nil => ("user-3@server.org","User-3","0x111111111","http://sever.org/user/icon-3.png")
              }
              UserClientHttp(uri)
                .withTimeout(timeout)
                .create(email,name,xid,avatar)
                .await()
            case "get" :: id :: Nil => 
              UserClientHttp(uri)
                .withTimeout(timeout)
                .get(UUID(id))
                .await()
            case "findByEid" :: xid :: Nil => 
              UserClientHttp(uri)
                .withTimeout(timeout)
                .findByXid(xid)
                .await()
            case "all" :: Nil => 
              UserClientHttp(uri)
                .withTimeout(timeout)
                .all()
                .await()

            case Nil => UserClientHttp(uri)
                .withTimeout(timeout)
                .all()
                .await()

            case _ => 
              Console.err.println(s"unknown op: ${config.params}")
              sys.exit(1)
          }
        
        Console.err.println(s"${r}")
        System.exit(0)
      }
    }
  }
}