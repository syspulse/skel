package io.syspulse.skel.ai.mcp

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel
import io.syspulse.skel.Command
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  /** Base path for `io.syspulse.skel.Server.run`; default depends on command (`/api/v1/server` vs `/api/v1/mcp`). */
  uri:String = "/api/v1/server",

  // —— ConfigMcp ——
  serverName: String = "skel-mcp",
  serverVersion: String = "1.0.0",
  protocolVersion: String = "2024-11-05",
    
  filter:String = "",
  
  limit:Long = Long.MaxValue,
  size:Long = Long.MaxValue,

  feed:String = "stdin://",
  output:String = "stdout://",
  
  delimiter:String = "\n",
  buffer:Int = 8192 * 100,
  throttle:Long = 0L,  
  format:String = "",

  apiKey:String = "",
  
  cmd:String = "server",
  params: Seq[String] = Seq(),
  
) extends ConfigMcp

object App extends skel.Server {

  def main(args:Array[String]): Unit = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")

    val d = Config()

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-mcp","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: server→/api/v1/server, mcp→/api/v1/mcp)"),
        
        ArgString('f', "feed",s"Input Feed (stdin://, http://, file://, kafka://) (def=${d.feed})"),
        ArgString('o', "output",s"Output (stdout://, csv://, json://, log://, file://, hive://, elastic://, kafka:// (def=${d.output})"),

        ArgLong('n', s"limit",s"File Limit (def: ${d.limit})"),
        ArgLong('s', s"size",s"File Size Limit (def: ${d.size})"),
        ArgString('_', "delimiter",s"""Delimiter characteds (def: '${Util.hex(d.delimiter.getBytes())}'). Usage example: --delimiter=`echo -e "\\r\\n"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),
        ArgString('_', "format",s"Format output (json,csv,log) (def=${d.format})"),
        
        ArgString('_', "api.key",s"API Key (URI path) (def=${d.apiKey})"),

        ArgString('_', "mcp.server.name", s"MCP server name in initialize (def: ${d.serverName})"),
        ArgString('_', "mcp.server.version", s"MCP server version (def: ${d.serverVersion})"),
        ArgString('_', "mcp.protocol.version", s"MCP protocol version (def: ${d.protocolVersion})"),
                
        ArgCmd("server","HTTP: AppServer + MCP under …/mcp"),
        ArgCmd("mcp","HTTP: MCP-only at http.uri (e.g. /api/v1/mcp)"),
        ArgCmd("proxy","Proxy Command"),
        
        ArgParam("<processors>","List of processors (none/map,print,dedup)"),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val cmd = c.getCmd().getOrElse(d.cmd)
    val defaultUri = cmd match {
      case "mcp" => "/api/v1/mcp"
      case _     => "/api/v1/server"
    }

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(defaultUri),
      
      feed = c.getString("feed").getOrElse(d.feed),
      output = c.getString("output").getOrElse(d.output),

      limit = c.getLong("limit").getOrElse(d.limit),
      size = c.getLong("size").getOrElse(d.size),      
      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),      
      format = c.getString("format").getOrElse(d.format),

      apiKey = c.getString("api.key").getOrElse(d.apiKey),

      serverName = c.getString("mcp.server.name").getOrElse(d.serverName),
      serverVersion = c.getString("mcp.server.version").getOrElse(d.serverVersion),
      protocolVersion = c.getString("mcp.protocol.version").getOrElse(d.protocolVersion),

      filter = c.getString("filter").getOrElse(d.filter),
      
      cmd = cmd,
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    val filter = config.filter + config.params.mkString(" ")

    /** Default tool set used by the CLI. */
    def defaultTools: Seq[McpTool] = Seq(EchoMcpTool(), AddMcpTool())

    val r = config.cmd match {
      case "server" =>
        run(
          config.host,
          config.port,
          config.uri,
          c,
          Seq(
            (
              Behaviors.ignore[Command],
              "AppServer",
              (_: ActorRef[Command], ac) => new AppServer(config, config.uri, McpServer.defaultTools)(ac)
            )
          )
        )
      case "mcp" =>
        run(
          config.host,
          config.port,
          config.uri,
          c,
          Seq(
            (
              Behaviors.ignore[Command],
              "McpServer",
              (_: ActorRef[Command], ac) =>
                new McpRoutes(config, config.uri.stripSuffix("/"), McpServer.defaultTools)(ac)
            )
          )
        )
      case _ =>
        Console.err.println(s"Unknown cmd: ${config.cmd}")
    }

    Console.err.println(s"r = ${r}")
  }
}
