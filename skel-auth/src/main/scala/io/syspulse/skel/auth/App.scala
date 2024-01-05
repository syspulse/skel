package io.syspulse.skel.auth

import scala.util.Success
import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.user.server.UserRoutes
import io.syspulse.skel.user.store._

import io.syspulse.skel.auth.server.AuthRoutes
import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.store._
import io.syspulse.skel.auth.permissions.casbin.PermissionsCasbin
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permissions.DefaultPermissions
import io.syspulse.skel.auth.cred.Cred

import io.syspulse.skel.auth.cred._
import io.syspulse.skel.auth.code._
import io.syspulse.skel.auth.permit._
import io.syspulse.skel.auth.store._
import io.syspulse.skel.auth.permit.PermitStoreCasbin
import io.syspulse.skel.auth.permit.PermitStoreDir
import io.syspulse.skel.auth.permit.PermitRegistry
import io.syspulse.skel.auth.permit.PermitStoreMem
import io.syspulse.skel.auth.permit.PermitStoreRbac
import io.syspulse.skel.auth.permit.PermitStoreRbacDemo
import java.math.BigInteger
import java.util.Base64
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.security.cert.CertificateFactory
import java.io.ByteArrayInputStream
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/auth",

  datastore:String = "mem://",
  storeCode:String = "mem://",
  storeCred:String = "mem://",
  storePermissions:String = "rbac://", //"casbin://",

  // legacy investion research
  proxyBasicUser:String = "user1",
  proxyBasicPass:String = "pass1",
  proxyBasicRealm:String = "realm",
  proxyUri:String = "http://localhost:8080/api/v1/auth/proxy",
  proxyBody:String = """{ "username":{{user}}, "password":{{pass}}""",
  proxyHeadersMapping:String = "HEADER:Content-type:application/json, HEADER:X-App-Id:{{client_id}}, HEADER:X-App-Secret:{{client_secret}}, BODY:X-User:{{user}}, BODY:X-Pass:{{pass}}",

  // jwtSecret:Option[String] = None,
  // jwtAlgo:String = "HS512",
  jwtUri:String = "hs512://",

  jwtRoleService:String = "",
  jwtRoleAdmin:String = "",

  userUri:String = "http://localhost:8080/api/v1/user",
  
  permissionsModel:String = "conf/permissions-model-rbac.conf",
  permissionsPolicy:String = "conf/permissions-policy-rbac.csv",

  cmd:String = "",
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
      new ConfigurationArgs(args,"skel-auth","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        ArgString('_', "store.code",s"Datastore for Codes (def: ${d.storeCode})"),
        ArgString('_', "store.cred",s"Datastore for Creds (def: ${d.storeCred})"),
        ArgString('_', "store.permissions",s"Datastore for Permissions (def: ${d.storePermissions})"),
        
        ArgString('_',"proxy.basic.user",s"Auth Basic Auth username (def: ${d.proxyBasicUser})"),
        ArgString('_',"proxy.basic.pass",s"ProxyM2M Auth Basic Auth password (def: ${d.proxyBasicPass}"),
        ArgString('_',"proxy.uri",s"ProxyM2M Auth server endpoint (def: ${d.proxyUri}"),
        ArgString('_',"proxy.body",s"ProxyM2M Body mapping (def: ${d.proxyBody}) "),
        ArgString('_',"proxy.headers.mapping",s"ProxyM2M Headers mapping (def: ${d.proxyHeadersMapping}) "),

        // ArgString('_', "jwt.secret",s"JWT secret (def: ${d.jwtSecret})"),
        // ArgString('_', "jwt.algo",s"JWT Algo [HS512,RS512,...] (def: ${d.jwtAlgo})"),
        ArgString('_', "jwt.uri",s"JWT Uri [hs512://secret,rs512://pk/key,rs512://sk/key] (def: ${d.jwtUri})"),

        ArgString('_', "jwt.role.service",s"JWT access_token for Service Account (def: ${d.jwtRoleService})"),
        ArgString('_', "jwt.role.admin",s"JWT access_token for Admin Account (def: ${d.jwtRoleAdmin})"),

        ArgString('_', "user.uri",s"User Service URI (def: ${d.userUri})"),

        ArgString('_', "permissions.model",s"RBAC model file (def: ${d.permissionsModel}"),
        ArgString('_', "permissions.policy",s"User Roles (def: ${d.permissionsPolicy}"),

        ArgCmd("server",s"Server"),
        ArgCmd("demo",s"Server with embedded UserServices (for testing)"),
        ArgCmd("client",s"Http Client"),
        ArgCmd("jwt",s"JWT subcommands: \n" +
          s"encode k=v k=v  : generate JWT with map\n" +
          s"decode <jwt>    : decode JWT\n" +
          s"valid <jwt>     : validate JWT\n" +
          s"admin           : create Admin role token\n"+
          s"service         : create Service role token\n"+
          s"user <uid>      : create User role token\n"
        ),
        ArgCmd("cred",s"Client Credentials subcommands: \n" +
          s"generate        : generate Client Credentials pair\n" +
          ""
        ),
        ArgCmd("permission",s"Permissions subcommand:\n" +
          s"jwt <resource> [action]  : Verify user has 'action' permissions for 'resource' (def action=write)\n"
        ),
        ArgCmd("role",s"Role subcommands\n" +
          s"jwt <role> : Verify user has 'role'\n"
        ),
        ArgCmd("permit",s"Validation permission (as in routes Service)\n" +
          s"jwt <role> : Verify user has 'role' (default is admin)\n"
        ),
        ArgCmd("jwks",s"JWT Key stores\n" +
          s"get <uri> : Get JWKS from uri\n"
        ),
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),
      storeCode = c.getString("store.code").getOrElse(d.storeCode),
      storeCred = c.getString("store.cred").getOrElse(d.storeCred),
      storePermissions = c.getString("store.permissions").getOrElse(d.storePermissions),

      proxyBasicUser = c.getString("proxy.basic.user").getOrElse(d.proxyBasicUser),
      proxyBasicPass = c.getString("proxy.basic.pass").getOrElse(d.proxyBasicPass),

      proxyUri = c.getString("proxy.uri").getOrElse(d.proxyUri),
      proxyBody = c.getString("proxy.body").getOrElse(d.proxyBody),
      proxyHeadersMapping = c.getString("proxy.headers.mapping").getOrElse(d.proxyHeadersMapping),

      // jwtSecret = c.getSmartString("jwt.secret"),
      // jwtAlgo = c.getString("jwt.algo").getOrElse(d.jwtAlgo),
      jwtUri = c.getString("jwt.uri").getOrElse(d.jwtUri),

      jwtRoleService = c.getSmartString("jwt.role.service").getOrElse(""),
      jwtRoleAdmin = c.getSmartString("jwt.role.admin").getOrElse(""),

      userUri = c.getString("user.uri").getOrElse(d.userUri),

      permissionsModel = c.getString("permissions.model").getOrElse(d.permissionsModel),
      permissionsPolicy = c.getString("permissions.policy").getOrElse(d.permissionsPolicy),

      cmd = c.getCmd().getOrElse("server"),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    log.debug(s"config=${config}")

    val authStore = config.datastore.split("://").toList match {
      //case "mysql" | "db" => new AuthStoreDB(c,"mysql")
      //case "postgres" => new AuthStoreDB(c,"postgres")
      case "dir" :: Nil => new AuthStoreDir()
      case "dir" :: dir :: Nil => new AuthStoreDir(dir)
      case "mem" :: _ | "cache" :: _ => new AuthStoreMem
      case _ => {
        Console.err.println(s"Uknown auth datastore: '${config.datastore}'")
        sys.exit(1)
      }
    }

    val codeStore = config.storeCode.split("://").toList match {
      case "mem" :: _ | "cache" :: _ => new CodeStoreMem()
      case _ => {
        Console.err.println(s"Uknown code store: '${config.storeCode}'")        
        sys.exit(1)
      }
    }

    val credStore = config.storeCred.split("://").toList match {
      case "dir" :: Nil => new CredStoreDir()
      case "dir" :: dir :: Nil => new CredStoreDir(dir)
      case "mem" :: _ | "cache" :: _ => new CredStoreMem()
      case _ => {
        Console.err.println(s"Uknown cred store: '${config.storeCred}'")        
        sys.exit(1)
      }
    }

    val permissionsStore = config.storePermissions.split("://").toList match {
      case "casbin" :: _  => new PermitStoreCasbin()

      case "dir" :: Nil => new PermitStoreDir()
      case "dir" :: dir :: Nil => new PermitStoreDir(dir) 

      case "rbac" :: "demo" :: Nil => new PermitStoreRbacDemo()
      case "rbac" :: ext :: Nil => new PermitStoreRbac(ext)
      case "rbac" :: Nil => new PermitStoreRbac()

      case "mem" :: _ | "cache" :: _ => new PermitStoreMem()
      case _ => {
        Console.err.println(s"Uknown permissions store: '${config.storePermissions}'")        
        sys.exit(1)
      }
    }


    if(! config.jwtUri.isBlank()) {
      AuthJwt(config.jwtUri)
    }

    val authHost = if(config.host=="0.0.0.0") "localhost" else config.host

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri,c,
          Seq(
            (AuthRegistry(authStore),"AuthRegistry",(authRegistry, context) => {
                val codeRegistry = context.spawn(CodeRegistry(codeStore),"Actor-CodeRegistry")
                val credRegistry = context.spawn(CredRegistry(credStore),"Actor-ClientRegistry")
                val permissionsRegistry = context.spawn(PermitRegistry(permissionsStore),"Actor-PermissionsRegistry")
                new AuthRoutes(authRegistry,codeRegistry,credRegistry,permissionsRegistry,
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

        implicit val userConfig = io.syspulse.skel.user.Config( )

        run( config.host, config.port, uri, c,
          Seq(
            (AuthRegistry(authStore),"AuthRegistry",(authRegistry, context) => {
                val codeRegistry = context.spawn(CodeRegistry(codeStore),"Actor-CodeRegistry")
                val credRegistry = context.spawn(CredRegistry(credStore),"Actor-ClientRegistry")
                val permissionsRegistry = context.spawn(PermitRegistry(permissionsStore),"Actor-PermissionsRegistry")
                new AuthRoutes(authRegistry,codeRegistry,credRegistry,permissionsRegistry,
                  s"http://${authHost}:${config.port}${uri}/auth",
                  s"http://${authHost}:${config.port}${uri}/auth/callback",
                  s"http://${authHost}:${config.port}${uri}/user")(context, config) 
              }
              .withSuffix("auth")
            ),
            (UserRegistry(new UserStoreMem),"UserRegistry",(a, ac) => new UserRoutes(a)(ac,userConfig).withSuffix("user") )
            
          )
        )
        // generate Admin token for testing
        val adminAccessTokenFile = "ACCESS_TOKEN_ADMIN"
        val adminAccessToken = AuthJwt().generateAccessToken(Map("uid" -> DefaultPermissions.USER_ADMIN.toString))
        os.write.over(os.Path(adminAccessTokenFile,os.pwd),adminAccessToken + "\n")
        Console.err.println(s"${Console.GREEN}${adminAccessTokenFile}:${Console.RESET} ${adminAccessToken}")

      case "client" => {
                      
        sys.exit(0)
      }
      
      case "permission" => {
        
        def resolvePermissions(jwt:String,resource:String,action:String) = {
          
          Console.err.println(s"Permissions: ${resource}:${action}: ${jwt}")

          //val exp = AuthJwt.DEFAULT_ACCESS_TOKEN_SERVICE_TTL
          //val jwt = AuthJwt.generateAccessToken(Map("role" -> role),expire = exp)
          val vt = AuthJwt.verifyAuthToken(Some(jwt),"",Seq())
          if(!vt.isDefined) {
            Console.err.println(s"not valid: ${jwt}")
            sys.exit(1)
          }
                    
          implicit val permissions = permissionsStore.getEngine().get
          Permissions.isAllowed(resource,action,AuthenticatedUser(UUID(vt.get.uid),vt.get.roles))
        }

        val r = 
          config.params match {
            case jwt :: resource :: Nil => 
              resolvePermissions(jwt,resource,"read")

            case jwt :: resource :: action :: Nil => 
              resolvePermissions(jwt,resource,action)

            case jwt :: Nil => 
              resolvePermissions(jwt,"*","write")

            case _ => Console.err.println(s"unknown operation: ${config.params.mkString("")}")
          }
        
        println(s"${r}")
        System.exit(0)
      }

      case "role" => {
        
        def resolveRole(jwt:String,role:String) = {
          
          Console.err.println(s"Role: ${role}: ${jwt}")

          val vt = AuthJwt.verifyAuthToken(Some(jwt),"",Seq())
          if(!vt.isDefined) {
            Console.err.println(s"not valid: ${jwt}")
            sys.exit(1)
          }
          
          // if(!permissionsStore.getEngine().isDefined) {
          //   Console.err.println(s"store does not support enforcer: ${permissionsStore}")
          //   sys.exit(1)
          // }

          implicit val permissions = permissionsStore.getEngine().get
          Permissions.hasRole(role,AuthenticatedUser(UUID(vt.get.uid),vt.get.roles))
        }

        val r = 
          config.params match {
            case jwt :: role :: Nil => 
              resolveRole(jwt,role)

            case jwt :: Nil => 
              resolveRole(jwt,"user")
            
            case _ => Console.err.println(s"unknown operation: ${config.params.mkString("")}")
          }
        
        println(s"${r}")
        System.exit(0)
      }

      case "permit" => {
        implicit val permissions = permissionsStore.getEngine().get
        
        val r = 
          config.params match {
            case jwt :: tail if (tail == Nil || tail == "admin" :: Nil) => 
              val vt = AuthJwt.verifyAuthToken(Some(jwt),"",Seq())
                  if(!vt.isDefined) {
                Console.err.println(s"not valid: ${jwt}")
                sys.exit(1)
              }
              Permissions.isAdmin(AuthenticatedUser(UUID(vt.get.uid),vt.get.roles))

            case jwt :: "service" :: Nil => 
              val vt = AuthJwt.verifyAuthToken(Some(jwt),"",Seq())
                  if(!vt.isDefined) {
                Console.err.println(s"not valid: ${jwt}")
                sys.exit(1)
              }
              Permissions.isService(AuthenticatedUser(UUID(vt.get.uid),vt.get.roles))
            
            case _ => 
              Console.err.println(s"unknown operation: ${config.params.mkString("")}")
          }
        
        println(s"${r}")
        System.exit(0)
      }

      case "jwt" => {        
        val r = 
          config.params match {
            case "admin" :: ttl => 
              // long living token
              val exp = if(ttl == Nil) AuthJwt.DEFAULT_ACCESS_TOKEN_ADMIN_TTL else ttl.head.toLong
              val aj = AuthJwt(config.jwtUri)
              aj.generateAccessToken(
                Map("uid" -> DefaultPermissions.USER_ADMIN.toString, "roles" -> "admin"),
                expire = exp                
              )
            
            case "service" :: ttl => 
              // long living token
              val exp = if(ttl == Nil) AuthJwt.DEFAULT_ACCESS_TOKEN_SERVICE_TTL else ttl.head.toLong
              val aj = AuthJwt(config.jwtUri)
              aj.generateAccessToken(
                Map("uid" -> DefaultPermissions.USER_SERVICE.toString, "roles" -> "service"),
                expire = exp
              )

            case "user" :: uid :: ttl => 
              val exp = if(ttl == Nil) AuthJwt.DEFAULT_ACCESS_TOKEN_SERVICE_TTL else ttl.head.toLong
              val aj = AuthJwt(config.jwtUri)
              aj.generateAccessToken(
                Map("uid" -> uid,"roles" -> "user")                
              )

            case "encode" :: data => 
              val exp = AuthJwt.DEFAULT_ACCESS_TOKEN_SERVICE_TTL
              val aj = AuthJwt(config.jwtUri)
              aj.generateAccessToken(
                data.map(_.split("=")).collect{ case(Array(k,v)) => k->v}.toMap,
                expire = exp
              )

            case "decode" :: token :: Nil => 
              val aj = AuthJwt(config.jwtUri)
              aj.decodeAll(token) match {
                case Success(jwt) => 
                  s"valid = ${jwt._1}\n"+
                  s"header = ${jwt._2.algorithm},${jwt._2.contentType},${jwt._2.keyId},${jwt._2.typ}\n" +
                  s"claim = ${jwt._3.audience},${jwt._3.content},${jwt._3.expiration},${jwt._3.issuedAt},${jwt._3.issuer},${jwt._3.jwtId},${jwt._3.notBefore},${jwt._3.subject}\n" + 
                  s"sig = ${jwt._4}"
                case f => f
              }
            case "valid" :: token :: Nil => 
              AuthJwt().isValid(token)

            case _ => Console.err.println(s"unknown operation: ${config.params.mkString("")}")
          }
        
        println(s"${r}")
        System.exit(0)
      }

      case "cred" => {
        val r = 
          config.params match {
            case "generate" :: Nil => 
              val client_id = Cred.generateClientId()
              val client_secret = Cred.generateClientSecret()
                        
              s"""export ETH_AUTH_CLIENT_ID="${client_id}"\n"""+
              s"""export ETH_AUTH_CLIENT_SECRET="${client_secret}"\n"""

            case _ => Console.err.println(s"unknown operation: ${config.params.mkString("")}")
          }
        
        println(s"${r}")
        System.exit(0)
      }

      case "jwks" => {       

        def getPublicKey(jwks:String) = {
          val json = ujson.read(jwks)
          val n = json.obj("keys").arr(0).obj("n").str
          val e = json.obj("keys").arr(0).obj("e").str
          val x5c = json.obj("keys").arr(0).obj("x5c").arr(0).str

          val modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n))
          val exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e))
          val publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent))
          
          // val factory = CertificateFactory.getInstance("X.509")
          // val x5cData = Base64.getDecoder.decode(x5c)
          // val cert = factory
          //     .generateCertificate(new ByteArrayInputStream(x5cData))              
          // val publicKey = cert.getPublicKey().asInstanceOf[RSAPublicKey]
          log.debug(s"PK: ${publicKey}")
          publicKey
        }

        val r = 
          config.params match {
            case "get" :: uri :: Nil =>               
              val jwks = uri.split("://").toList match {
                case ("http" | "https") :: url =>
                  val r = requests.get(uri)
                  r.text()
                case "file" :: url :: Nil => // file
                  os.read(os.Path(url,os.pwd))
                case _ => 
                  os.read(os.Path(uri,os.pwd))
              }              
              getPublicKey(jwks)

            case "verify" :: uri :: token :: Nil =>               
              val jwks = uri.split("://").toList match {
                case ("http" | "https") :: url =>
                  val r = requests.get(uri)
                  r.text()
                case "file" :: url :: Nil => // file
                  os.read(os.Path(url,os.pwd))
                case _ => 
                  os.read(os.Path(uri,os.pwd))
              }              
              val pk = getPublicKey(jwks)
              val valid = Jwt.isValid(token, pk, Seq(JwtAlgorithm.RS256,JwtAlgorithm.RS512))
              s"PK: ${pk}\nhex: ${Util.hex(pk.getEncoded())}\nvalid=${valid}"

            case _ => Console.err.println(s"unknown operation: ${config.params.mkString("")}")
          }
        
        println(s"${r}")
        System.exit(0)
      }
    }    
  }
}



