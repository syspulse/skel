package io.syspulse.skel.auth

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.user._
import io.syspulse.skel.auth.{AuthRegistry,AuthRoutes}
import io.syspulse.skel.auth.jwt.AuthJwt


import scopt.OParser
import scala.util.Success

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

  permissionsModel:String = "",
  permissionsPolicy:String = "",

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
        ArgString('u', "http.uri","api uri (def: /api/v1/auth)"),
        ArgString('d', "datastore","datastore [mysql,postgres,mem,cache] (def: mem)"),
        
        ArgString('_',"proxy.basic.user","Auth Basic Auth username (def: user1"),
        ArgString('_',"proxy.basic.pass","ProxyM2M Auth Basic Auth password (def: pass1"),
        ArgString('_',"proxy.uri","ProxyM2M Auth server endpoint (def: http://localhost:8080/api/v1/auth/m2m"),
        ArgString('_',"proxy.body","ProxyM2M Body mapping (def:) "),
        ArgString('_',"proxy.headers.mapping","ProxyM2M Headers mapping (def:) "),

        ArgString('_', "jwt.secret","JWT secret"),

        ArgString('_', "service.user.uri","User Service URI (def: http://localhost:8080/api/v1/user)"),

        ArgString('_', "permissions.model","RBAC model file (def: permissions-model-rbac.conf"),
        ArgString('_', "permissions.policy","User Roles (def: permissions-policy-rbac.csv"),

        ArgCmd("server","Server"),
        ArgCmd("server-with-user","Server with embedded UserServices (for testing)"),
        ArgCmd("client","Http Client"),
        ArgCmd("jwt","JWT utils (encode/decode)"),
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

      permissionsModel = c.getString("permissions.model").getOrElse("conf/permissions-model-rbac.conf"),
      permissionsPolicy = c.getString("permissions.policy").getOrElse("conf/permissions-policy-rbac.csv"),

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

    AuthJwt.run(config.jwtSecret)
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
      case "server-with-user" =>
        val uri = Util.getParentUri(config.uri)
        println(s"${Console.YELLOW}Running with AuthService(mem):${Console.RESET} http://${authHost}:${config.port}${uri}/auth")
        println(s"${Console.YELLOW}Running with UserService(mem):${Console.RESET} http://${authHost}:${config.port}${uri}/user")
        run( config.host, config.port, uri, c,
          Seq(
            (AuthRegistry(store),"AuthRegistry",(actor, context) => {
                new AuthRoutes(actor,
                  s"http://${authHost}:${config.port}${uri}/auth",
                  s"http://${authHost}:${config.port}${uri}/auth/callback",
                  s"http://${authHost}:${config.port}${uri}/user")(context, config) 
              }
              .withSuffix("auth")
            ),
            (UserRegistry(new UserStoreMem),"UserRegistry",(a, ac) => new UserRoutes(a)(ac).withSuffix("user") )
            
          )
        )
        // generate Admin token for testing
        val adminAccessTokenFile = "ACCESS_TOKEN_ADMIN"
        val adminAccessToken = AuthJwt.generateAccessToken(Map("uid" -> "ffffffff-0000-0000-9000-000000000001"))        
        os.write.over(os.Path(adminAccessTokenFile,os.pwd),adminAccessToken + "\n")
        println(s"${Console.GREEN}${adminAccessTokenFile}:${Console.RESET} ${adminAccessToken}")

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
      case "jwt" => {
        
        val r = 
          config.params match {
            case "encode" :: uid :: Nil => 
              AuthJwt.generateAccessToken(Map("uid" -> uid))

            case "decode" :: token :: Nil => 
              AuthJwt.decodeAll(token) match {
                case Success(jwt) => jwt
                case f => f
              }
            case "valid" :: token :: Nil => 
              AuthJwt.isValid(token)

            case _ => println(s"unknown op: ${config.params}")
          }
        
        println(s"${r}")
        System.exit(0)
      }
    }    
  }
}



