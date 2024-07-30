package io.syspulse.skel.plugin

import scala.util.Success

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin.store._
import store.PluginStoreMem

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/plugin",

  datastore:String = "dir://",
  
  registry:Seq[String] = Seq(""),
  
  cmd:String = "runtime",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  def prompt() = {
    Console.err.println("press [Enter] to gracefully continue, or [CTRL+C] to abort")
    Console.in.readLine()
  }

  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-plugin","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        
        ArgString('d', "datastore",s"Datastore [dir://] (def: ${d.datastore})"),
        
        ArgString('r', "registry",s"Extra Registry (def: ${d.registry})"),
        
        ArgCmd("server",s"Server"),        
        
        ArgCmd("registry",s"Show Execs Registry"),

        ArgCmd("runtime",s"Runtime subcommands: " +
          s"spawn <id>          : Spawn + start Plugin"+
          s"run <id>            : Spawn + start + wait Plugin"+
          s"start <wid>         : Start spawned Plugining"+
          s"stop <wid>          : Stop running Plugining"+
          s"kill <wid>          : Kill with deleting all states and data"+          
          ""
        ),        
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),
      
      registry = c.getListString("registry",d.registry),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val store = config.datastore.split("://").toList match {
      case ("classpath" | "class") :: className :: Nil =>  new PluginStoreClasspath(className)
      case "dir" :: Nil =>  new PluginStoreDir()
      case "dir" :: dir :: Nil => new PluginStoreDir(dir)
      case "jars" :: mask :: Nil => new PluginStoreDir(classMask = Some(mask))
      case "jars" :: dir :: mask :: Nil => new PluginStoreDir(dir,classMask=Some(mask))
      case _ => new PluginStoreMem()
    }

    Console.err.println(s"store: ${store}")

    store.loadPlugins()
    
    val r = config.cmd match {
      case "server" => 
      
      case "runtime" => {        
        //val runtime = new ClassRuntime()
        val runtime = new PluginEngine(store)

        config.params match {
          case "spawn" :: id :: Nil =>
            for {
              plugin <- store.?(id)  
              r <- runtime.spawn(plugin)       
            } yield r

          case "start" :: id :: Nil =>
            runtime.start(id)
          
          case "start" :: Nil =>
            // for {
            //   plugin <- store.all
            //   r <- Seq(runtime.spawn(plugin))
            // } yield r
            runtime.start()
                  
          case _ => 
            for {
              pp <- store.all
            } yield pp
        }}
    }

    println(s"${r}")
    sys.exit(0) 
  }
}



