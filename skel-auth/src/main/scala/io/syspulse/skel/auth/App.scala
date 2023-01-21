package io.syspulse.skel.auth

import scala.util.Success

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.user.server.UserRoutes
import io.syspulse.skel.user.store._

import io.syspulse.skel.auth.server.AuthRoutes
import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.store._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/auth",
  datastore:String = "mem",

  proxyBasicUser:String = "user1",
  proxyBasicPass:String = "pass1",
  proxyBasicRealm:String = "realm",
  proxyUri:String = "http://localhost:8080/api/v1/auth/m2m",
  proxyBody:String = """{ "username":{{user}}, "password":{{pass}}""",
  proxyHeadersMapping:String = "HEADER:Content-type:application/json, HEADER:X-App-Id:{{client_id}}, HEADER:X-App-Secret:{{client_secret}}, BODY:X-User:{{user}}, BODY:X-Pass:{{pass}}",

  jwtSecret:String = "",

  userUri:String = "http://localhost:8080/api/v1/user",

  permissionsModel:String = "conf/permissions-model-rbac.conf",
  permissionsPolicy:String = "conf/permissions-policy-rbac.csv",

  cmd:String = "",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]):Unit = {
    println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-auth","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        
        ArgString('_',"proxy.basic.user",s"Auth Basic Auth username (def: ${d.proxyBasicUser})"),
        ArgString('_',"proxy.basic.pass",s"ProxyM2M Auth Basic Auth password (def: ${d.proxyBasicPass}"),
        ArgString('_',"proxy.uri",s"ProxyM2M Auth server endpoint (def: ${d.proxyUri}"),
        ArgString('_',"proxy.body",s"ProxyM2M Body mapping (def: ${d.proxyBody}) "),
        ArgString('_',"proxy.headers.mapping",s"ProxyM2M Headers mapping (def: ${d.proxyHeadersMapping}) "),

        ArgString('_', "jwt.secret",s"JWT secret (def: ${d.jwtSecret})"),

        ArgString('_', "user.uri",s"User Service URI (def: ${d.userUri})"),

        ArgString('_', "permissions.model",s"RBAC model file (def: ${d.permissionsModel}"),
        ArgString('_', "permissions.policy",s"User Roles (def: ${d.permissionsPolicy}"),

        ArgCmd("server",s"Server"),
        ArgCmd("demo",s"Server with embedded UserServices (for testing)"),
        ArgCmd("client",s"Http Client"),
        ArgCmd("jwt",s"JWT utils (encode/decode/jwt)"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),

      proxyBasicUser = c.getString("proxy.basic.user").getOrElse(d.proxyBasicUser),
      proxyBasicPass = c.getString("proxy.basic.pass").getOrElse(d.proxyBasicPass),

      proxyUri = c.getString("proxy.uri").getOrElse(d.proxyUri),
      proxyBody = c.getString("proxy.body").getOrElse(d.proxyBody),
      proxyHeadersMapping = c.getString("proxy.headers.mapping").getOrElse(d.proxyHeadersMapping),

      jwtSecret = c.getString("jwt.secret").getOrElse(Util.generateRandomToken()),

      userUri = c.getString("user.uri").getOrElse(d.userUri),

      permissionsModel = c.getString("permissions.model").getOrElse(d.permissionsModel),
      permissionsPolicy = c.getString("permissions.policy").getOrElse(d.permissionsPolicy),

      cmd = c.getCmd().getOrElse("server"),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store = config.datastore match {
      //case "mysql" | "db" => new AuthStoreDB(c,"mysql")
      //case "postgres" => new AuthStoreDB(c,"postgres")
      case "mem" | "cache" => new AuthStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new AuthStoreMem
      }
    }

    AuthJwt.withSecret(config.jwtSecret)
    val authHost = if(config.host=="0.0.0.0") "localhost" else config.host

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (AuthRegistry(store),"AuthRegistry",(actor, context) => {
                new AuthRoutes(actor,
                  s"http://${authHost}:${config.port}${config.uri}",
                  s"http://${authHost}:${config.port}${config.uri}/callback", config.userUri)(context, config) 
              }
            )
            
          )
        )
      case "demo" =>
        val uri = Util.getParentUri(config.uri)
        Console.err.println(s"${Console.YELLOW}Running with AuthService(mem):${Console.RESET} http://${authHost}:${config.port}${uri}/auth")
        Console.err.println(s"${Console.YELLOW}Running with UserService(mem):${Console.RESET} http://${authHost}:${config.port}${uri}/user")
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
        Console.err.println(s"${Console.GREEN}${adminAccessTokenFile}:${Console.RESET} ${adminAccessToken}")

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

        //     case _ => Console.err.println(s"unknown op: ${config.params}")
        //   }
        // Console.err.println(r)
        
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

            case _ => Console.err.println(s"unknown operation: ${config.params.mkString("")}")
          }
        
        Console.err.println(s"${r}")
        System.exit(0)
      }
    }    
  }
}



