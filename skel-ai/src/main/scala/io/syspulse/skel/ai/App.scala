package io.syspulse.skel.ai

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.ActorSystem

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.auth.jwt.AuthJwt

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import io.syspulse.skel.ai._
import io.syspulse.skel.ai.store._
import io.syspulse.skel.ai.server._
import io.syspulse.skel.ai.core.Providers
import io.syspulse.skel.ai.core.AiURI

import io.syspulse.skel.ai.provider.openai.OpenAi
import io.syspulse.skel.ai.provider.AiProvider
import scala.util.Success
import scala.util.Failure

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/ai",

  datastore:String = "openai://",

  ai:String = "openai://",
  sys:String = "",

  // feed:String = "stdin://",
  // output:String = "stdout://",  
  // delimiter:String = "\n", //""
  // buffer:Int = 8192 * 100,
  // throttle:Long = 0L,
  // throttleSource:Long = 100L,
        
  cmd:String = "ask",
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
      new ConfigurationArgs(args,"skel-ai","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [mem://,gl://,ofac://] (def: ${d.datastore})"),
               
        // ArgString('f', "feed",s"Input Feed (stdin://, http://, file://, kafka://) (def=${d.feed})"),
        // ArgString('o', "output",s"Output (stdout://, csv://, json://, log://, file://, hive://, elastic://, kafka:// (def=${d.output})"),
        // ArgString('_', "delimiter",s"""Delimiter characteds (def: '${Util.hex(d.delimiter.getBytes())}'). Usage example: --delimiter=`echo -e "\\r\\n"` """),
        // ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        // ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),
        // ArgLong('_', "throttle.source",s"Throttle source (e.g. http, def=${d.throttleSource})"),
                
        ArgString('a', "ai",s"AI provider (def: ${d.ai})"),
        ArgString('s', "sys",s"System prompt (can reference file://) (def=${d.sys})"),

        ArgCmd("server","Server"),
        ArgCmd("store","Ask question from Store"),
        ArgCmd("ask","Ask question"),
        ArgCmd("chat","Chat"),
        ArgCmd("prompt","Prompt"),        

        ArgParam("<params>",""),
        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      
      datastore = c.getString("datastore").getOrElse(d.datastore),

      ai = c.getString("ai").getOrElse(d.ai),
      sys = c.getSmartString("sys").getOrElse(d.sys),

      // feed = c.getString("feed").getOrElse(d.feed),
      // output = c.getString("output").getOrElse(d.output),      
      // delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      // buffer = c.getInt("buffer").getOrElse(d.buffer),
      // throttle = c.getLong("throttle").getOrElse(d.throttle),
      // throttleSource = c.getLong("throttle.source").getOrElse(d.throttleSource),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    val store = config.datastore.split("://").toList match {
      case "openai" :: Nil => new AiStoreOpenAi(config.datastore)
      case "openai" :: uri :: Nil => new AiStoreOpenAi(uri)      
      // case "claude" :: Nil => new AiStoreClaude()
      // case "claude" :: dir :: _ => new AiStoreClaude(dir)
      case "mem" :: _ => new AiStoreMem()
      case _ => 
        Console.err.println(s"Unknown datastore: '${config.datastore}'")
        sys.exit(1)      
    }

    Console.err.println(s"Store: ${store}")
    Console.err.println(s"System prompt: ${config.sys}")
    
    val r = config.cmd match {
      case "server" =>
        
        run( config.host, config.port,config.uri,c,
          Seq(
            (AiRegistry(store),"AiRegistry",(reg, ac) => {              
              new AiRoutes(reg)(ac,config) 
            })
          )
        ) 

      case "store" => 
        config.params.toList match {
          case file :: _ if(file.startsWith("file://")) =>
            val text = os.read(os.Path(file.stripPrefix("file://"),os.pwd))
            store.????(text,None,Some(Providers.OPEN_AI))
          case _ => 
            store.????(config.params.mkString(" "),None,Some(Providers.OPEN_AI))
        }

      case "ask" => 
        ask(config.ai,config.params)(config)  

      case "chat" => 
        chat(config.ai,config.params)(config)
        
      case "prompt" => 
        prompt(config.ai,config.params)(config)
    }
    Console.err.println(s"r = ${r}")
  }

  def ask(uri:String, params:Seq[String])(config:Config):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val ai = AiURI(uri)

    val provider:AiProvider = ai.getProvider() match {
      case Providers.OPEN_AI => new OpenAi(uri)
      case _ => 
        Console.err.println(s"Unknown AI provider: '${ai.getProvider()}'")
        sys.exit(1)
    }

    val q0 = params.mkString(" ")
    Console.err.println(s"q0 = '${q0}'")

    for (i <- 1 to Int.MaxValue) {
      Console.err.print(s"${ai.getModel()}: ${i}> ")
      val q = scala.io.StdIn.readLine()
      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if(!q.isEmpty) {
        val p = provider.ask(q,ai.getModel(),Some(config.sys),10000,3)
        Console.err.println(s"${p.get}")
        val txt = p.get.answer.get
        Console.err.println(s"${Console.RED}${ai.getModel()}${Console.YELLOW}: ${txt}${Console.RESET}")
      }
    }
  }

  def chat(uri:String, params:Seq[String])(config:Config):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val ai = AiURI(uri)

    val provider:AiProvider = ai.getProvider() match {
      case Providers.OPEN_AI => new OpenAi(uri)
      case _ => 
        Console.err.println(s"Unknown AI provider: '${ai.getProvider()}'")
        sys.exit(1)
    }

    val q0 = params.mkString(" ")
    Console.err.println(s"q0 = '${q0}'")

    val p0 = Chat(
      messages = Seq(
        ChatMessage("system",config.sys),
        ChatMessage("user",q0)
      )
    )

    var p = p0

    for (i <- 1 to Int.MaxValue) {
      Console.err.print(s"${ai.getModel()}: [${p.messages.size}/${p.messages.map(_.content.size).sum}]: ${i}> ")
      val q = scala.io.StdIn.readLine()
      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if(!q.isEmpty) {
        
        val r = provider.chat(
          p.+(q),
          ai.getModel(),Some(config.sys),10000,3
        ) 
        
        r match {
          case Success(p1) => 
            p = p1
          case Failure(e) => 
            Console.err.println(s"Error: ${e}")            
        }

        Console.err.println(s"${r}")
        val txt = r.get.last.content
        Console.err.println(s"${Console.BLUE}${ai.getModel()}${Console.YELLOW}: ${txt}${Console.RESET}")
      }
    }
  }

  def prompt(uri:String, params:Seq[String])(config:Config):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val u = AiURI(uri)

    val provider:AiProvider = u.getProvider() match {
      case Providers.OPEN_AI => new OpenAi(uri)
      case _ => 
        Console.err.println(s"Unknown AI provider: '${u.getProvider()}'")
        sys.exit(1)
    }

    val a0 = Ai(
      question = params.mkString(" "),
      model = u.getModel(),
      xid = u.tid
    )

    Console.err.println(s"q0 = '${a0.question}'")

    var a = a0
    for (i <- 1 to Int.MaxValue) {
      Console.err.print(s"${a.model}: ${a.xid}: ${i}> ")
      val q = scala.io.StdIn.readLine()
      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if(!q.isEmpty) {
        val a1 = provider.prompt(a.copy(question = q),a.model,Some(config.sys),10000,3)
        Console.err.println(s"${a1.get}")
        val txt = a1.get.answer.get
        Console.err.println(s"${Console.GREEN}${a1.get.model}${Console.YELLOW}: ${txt}${Console.RESET}")
        a = a1.get
      }
    }
  }
}
