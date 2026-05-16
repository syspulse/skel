package io.syspulse.skel.explain

import scala.util.{Failure, Success}

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.auth.jwt.AuthJwt

import io.syspulse.skel.explain.store._
import io.syspulse.skel.explain.server._

case class Config(
  host: String = "0.0.0.0",
  port: Int = 8080,
  uri: String = "/api/v1/explain",

  datastore: String = "mem://",
  timeout: Long = 15000,

  jwtUri: String = "hs512://",
  ownerAttr: String = "oid",
  rolesAttr: String = "groups[].",
  serviceRole: String = "explain-service",
  adminRole: String = "explain-admin",
  permissions: String = "user",

  threads: Int = 16,
  env: String = "prod",

  guard: String = "allow",

  cmd: String = "server",
  params: Seq[String] = Seq()
)

object App extends skel.Server {

  def main(args: Array[String]): Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv,
      new ConfigurationArgs(args, "skel-explain", "",
        ArgString('h', "http.host", s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port", s"listen port (def: ${d.port})"),
        ArgString('u', "http.uri", s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore", s"DataStore [mem://,dir://,postgres://] (def: ${d.datastore})"),
        ArgLong('_', "timeout", s"operation timeout (def: ${d.timeout})"),

        ArgString('_', "jwt.uri", s"JWT Uri [hs512://secret,rs512://pk/key] (def: ${d.jwtUri})"),
        ArgString('_', "owner.attr", s"Owner attribute in JWT (def: ${d.ownerAttr})"),
        ArgString('_', "service.role", s"Service role in JWT (def: ${d.serviceRole})"),
        ArgString('_', "admin.role", s"Admin role in JWT (def: ${d.adminRole})"),
        ArgString('_', "permissions", s"Permissions mode (def: ${d.permissions})"),
        ArgString('_', "roles.attr", s"Roles attribute in JWT (def: ${d.rolesAttr})"),

        ArgString('_', "env", s"Environment (dev,prod) (def: ${d.env})"),
        ArgInt('_', "threads", s"Number of threads (def: ${d.threads})"),

        ArgString('g', "guard", s"Guard mode [allow,GuardName] (def: ${d.guard})"),

        ArgCmd("server", "Server only"),
        ArgCmd("migrate", "Migrate database"),

        ArgParam("<params>", ""),
        ArgLogging(),
        ArgConfig()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),

      datastore = c.getString("datastore").getOrElse(d.datastore),
      timeout = c.getLong("timeout").getOrElse(d.timeout),

      jwtUri = c.getString("jwt.uri").getOrElse(d.jwtUri),
      ownerAttr = c.getString("owner.attr").getOrElse(d.ownerAttr),
      serviceRole = c.getString("service.role").getOrElse(d.serviceRole).stripPrefix("'").stripSuffix("'"),
      adminRole = c.getString("admin.role").getOrElse(d.adminRole).stripPrefix("'").stripSuffix("'"),
      permissions = c.getString("permissions").getOrElse(d.permissions),
      rolesAttr = c.getString("roles.attr").getOrElse(d.rolesAttr),

      env = c.getString("env").getOrElse(d.env),
      threads = c.getInt("threads").getOrElse(d.threads),

      guard = c.getString("guard").getOrElse(d.guard),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams()
    )

    Console.err.println(s"Config: ${config}")

    if (!config.jwtUri.isBlank) {
      AuthJwt(config.jwtUri)
    }

    def getStore(uri: String): ExplainStore = {
      uri.split("://").toList match {
        case "mem" :: Nil         => new ExplainStoreMem()
        case "dir" :: Nil         => new ExplainStoreDir()
        case "dir" :: dir :: Nil  => new ExplainStoreDir(dir)
        case "postgres" :: Nil    => new ExplainStoreDB(c, "postgres://postgres")
        case "postgres" :: db :: Nil => new ExplainStoreDB(c, s"postgres://${db}")
        case "jdbc" :: _          => new ExplainStoreDB(c, uri)
        case _ =>
          Console.err.println(s"Unknown DataStore: '${uri}'")
          sys.exit(1)
      }
    }

    val store = getStore(config.datastore)
    Console.err.println(s"Store: ${store}")

    val r = config.cmd match {
      case "server" =>
        run(
          config.host, config.port, config.uri, c,
          Seq(
            (ExplainRegistry(store), "ExplainRegistry", (reg, ac) => {
              new ExplainRoutes(reg)(ac, config)
            })
          )
        )

      case "migrate" =>
        val storeTo = getStore(config.params.headOption.getOrElse("mem://"))
        val all = store.all
        var i = 0; var f = 0
        all.foreach { rule =>
          storeTo.+(rule) match {
            case Success(_) => i += 1
            case Failure(e) =>
              Console.err.println(s"Failed to migrate: ${e}")
              f += 1
          }
        }
        s"Migrated: ${i}/${f}/${all.size}"
    }

    Console.err.println(s"r = ${r}")
  }
}
