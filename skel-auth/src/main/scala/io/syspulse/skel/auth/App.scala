package io.syspulse.skel.auth

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}
import io.syspulse.skel.auth.{AuthRegistry,AuthRoutes}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = "",

  authBasicUser:String = "",
  authBasicPass:String = "",
  authBasicRealm:String = "realm",

  authUri:String = "",
  authBody:String = "",
  authHeadersMapping:String = "",

  files: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    println(s"args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('h', "http.host").action((x, c) => c.copy(host = x)).text("listen host (def: 0.0.0.0)"),
        opt[Int]('p', "http.port").action((x, c) => c.copy(port = x)).text("listern port (def: 8080)"),
        opt[String]('u', "http.uri").action((x, c) => c.copy(uri = x)).text("rest uri (def: /api/v1/otp)"),

        opt[String]('d', "datastore").action((x, c) => c.copy(datastore = x)).text("datastore"),

        opt[String]("auth.basic.user").action((x, c) => c.copy(uri = x)).text("Auth Basic Auth username (def: user1"),
        opt[String]("auth.basic.pass").action((x, c) => c.copy(uri = x)).text("Auth Basic Auth password (def: pass1"),

        opt[String]('a', "auth.uri").action((x, c) => c.copy(authUri = x)).text("Auth server endpoint (def: http://localhost:8080/api/v1/auth/m2m"),
        opt[String]("auth.body").action((x, c) => c.copy(authBody = x)).text("Body mapping (def:) "),
        opt[String]("auth.headers.mapping").action((x, c) => c.copy(authHeadersMapping = x)).text("Headers mapping (def:) "),

        help("help").text(s"${Util.info._1} microservice"),
        arg[String]("...").unbounded().optional().action((x, c) => c.copy(files = c.files :+ x)).text("files"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.default

        implicit val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else configuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else configuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else configuration.getString("http.uri").getOrElse("/api/v1/auth") },
          datastore = { if(! configArgs.datastore.isEmpty) configArgs.datastore else configuration.getString("datastore").getOrElse("cache") }.toLowerCase,

          authBasicUser = { if(! configArgs.authBasicUser.isEmpty) configArgs.authBasicUser else configuration.getString("auth.basic.user").getOrElse("user1") },
          authBasicPass = { if(! configArgs.authBasicPass.isEmpty) configArgs.authBasicPass else configuration.getString("auth.basic.pass").getOrElse("pass1") },

          authUri = { if(! configArgs.authUri.isEmpty) configArgs.authUri else configuration.getString("auth.uri").getOrElse("http://localhost:8080/api/v1/auth/m2m") },
          authBody = { if(! configArgs.authBody.isEmpty) configArgs.authBody else configuration.getString("auth.body")
                        .getOrElse("""{ "username":{{user}}, "password":{{pass}}""") },
          authHeadersMapping = { if(! configArgs.authHeadersMapping.isEmpty) configArgs.authHeadersMapping else configuration.getString("auth.headers.mapping")
                        .getOrElse("HEADER:Content-type:application/json, HEADER:X-App-Id:{{client_id}}, HEADER:X-App-Secret:{{client_secret}}, BODY:X-User:{{user}}, BODY:X-Pass:{{pass}}") },
        )

        println(s"Config: ${config}")

        run( config.host, config.port,config.uri, configuration,
          Seq(
            (AuthRegistry(),"AuthRegistry",(actor, context) => {
                //
                new AuthRoutes(actor,
                  s"http://localhost:${config.port}${config.uri}",
                  s"http://localhost:${config.port}${config.uri}/callback")(context, config) 
              }
            )
            
          )
        )
      }
      case _ => 
    }
  }
}



