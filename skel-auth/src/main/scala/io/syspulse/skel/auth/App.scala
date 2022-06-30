package io.syspulse.skel.auth

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.auth.{AuthRegistry,AuthRoutes}
import io.syspulse.skel.auth.jwt.AuthJwt

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = "",

  proxyBasicUser:String = "",
  proxyBasicPass:String = "",
  proxyBasicRealm:String = "realm",
  proxyUri:String = "",
  proxyBody:String = "",
  proxyHeadersMapping:String = "",

  jwtSecret:String = "",

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
      new ConfigurationArgs(args,"eth-stream","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/otp)"),
        ArgString('d', "datastore","datastore [mysql,postgres,mem,cache] (def: mem)"),
        
        ArgString('_',"proxy.basic.user","Auth Basic Auth username (def: user1"),
        ArgString('_',"proxy.basic.pass","ProxyM2M Auth Basic Auth password (def: pass1"),
        ArgString('_',"proxy.uri","ProxyM2M Auth server endpoint (def: http://localhost:8080/api/v1/auth/m2m"),
        ArgString('_',"proxy.body","ProxyM2M Body mapping (def:) "),
        ArgString('_',"proxy.headers.mapping","ProxyM2M Headers mapping (def:) "),

        ArgString('_', "jwt.secret","JWT secret"),

        ArgString('_', "service.user.uri","User Service URI (def: http://localhost:8080/api/v1/user)"),

        ArgCmd("server","Command"),
        ArgCmd("client","Command"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/auth"),
      datastore = c.getString("datastore").getOrElse("mem"),

      proxyBasicUser = c.getString("proxy.basic.user").getOrElse("user1"),
      proxyBasicPass = c.getString("proxy.basic.pass").getOrElse("pass1"),

      proxyUri = c.getString("proxy.uri").getOrElse("http://localhost:8080/api/v1/auth/m2m"),
      proxyBody = c.getString("proxy.body").getOrElse("""{ "username":{{user}}, "password":{{pass}}"""),
      proxyHeadersMapping = c.getString("proxy.headers.mapping")
                    .getOrElse("HEADER:Content-type:application/json, HEADER:X-App-Id:{{client_id}}, HEADER:X-App-Secret:{{client_secret}}, BODY:X-User:{{user}}, BODY:X-Pass:{{pass}}"),

      jwtSecret = c.getString("jwt.secret").getOrElse(Util.generateRandomToken()),

      serviceUserUri = c.getString("service.user.uri").getOrElse("http://localhost:8080/api/v1/user"),

      cmd = c.getCmd().getOrElse("server"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    val store = config.datastore match {
      //case "mysql" | "db" => new AuthStoreDB(c,"mysql")
      //case "postgres" => new AuthStoreDB(c,"postgres")
      case "mem" | "cache" => new AuthStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new AuthStoreMem
      }
    }

    AuthJwt.run(config)
    val authHost = if(config.host=="0.0.0.0") "localhost" else config.host

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (AuthRegistry(store),"AuthRegistry",(actor, context) => {
                new AuthRoutes(actor,
                  s"http://${authHost}:${config.port}${config.uri}",
                  s"http://${authHost}:${config.port}${config.uri}/callback",
                  config.serviceUserUri)(context, config) 
              }
            )
            
          )
        )
      case "client" => {
        
        // val host = if(config.host == "0.0.0.0") "localhost" else config.host
        // val uri = s"http://${host}:${config.port}${config.uri}"
        // val timeout = Duration("3 seconds")

        // val r = 
        //   config.params match {
        //     case "delete" :: id :: Nil => 
        //       OtpClientHttp(uri)
        //         .withTimeout(timeout)
        //         .delete(UUID(id))
        //         .await()
        //     case "create" :: userId :: Nil => 
        //       OtpClientHttp(uri)
        //         .withTimeout(timeout)
        //         .create(if(userId == "random") UUID.random else UUID(userId),"","name","account-2",None,None)
        //         .await()
        //     case "get" :: id :: Nil => 
        //       OtpClientHttp(uri)
        //         .withTimeout(timeout)
        //         .get(UUID(id))
        //         .await()
        //     case "getAll" :: Nil => 
        //       OtpClientHttp(uri)
        //         .withTimeout(timeout)
        //         .getAll()
        //         .await()
        //     case "getForUser" :: userId :: Nil => 
        //       OtpClientHttp(uri)
        //         .withTimeout(timeout)
        //         .getForUser(UUID(userId))
        //         .await()
        //     case Nil => OtpClientHttp(uri)
        //         .withTimeout(timeout)
        //         .getAll()
        //         .await()

        //     case _ => println(s"unknown op: ${config.params}")
        //   }
        
        // println(s"${r}")
        System.exit(0)
      }
    }    
  }
}



