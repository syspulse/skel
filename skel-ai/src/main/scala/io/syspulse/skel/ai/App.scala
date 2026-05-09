package io.syspulse.skel.ai

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.Logger

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
import scala.concurrent.ExecutionContext


case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/ai",

  datastore:String = "mem://",

  ai:String = "openai://",
  sys:String = "",
  images:Seq[String] = Seq(),

  // feed:String = "stdin://",
  // output:String = "stdout://",  
  // delimiter:String = "\n", //""
  // buffer:Int = 8192 * 100,
  // throttle:Long = 0L,
  // throttleSource:Long = 100L,
        
  cmd:String = "prompt-stream",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {

  def main(args:Array[String]):Unit = {
    
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
        ArgString('i', "images",s"Images (def=${d.images})"),

        ArgCmd("server","Server"),
        ArgCmd("store","Ask question from Store"),
        ArgCmd("ask","Ask question"),
        ArgCmd("chat","Chat"),
        ArgCmd("prompt","Prompt"),
        ArgCmd("prompt-stream","Prompt stream"),
        ArgCmd("messages","Anthropic Messages API (non-streaming)"),
        ArgCmd("messages-stream","Anthropic Messages API (streaming)"),
        ArgCmd("stream","Prompt stream"),
        ArgCmd("responses","Responses"), // same as prompt-stream

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
      images = c.getListStringDumb("images",d.images),

      // feed = c.getString("feed").getOrElse(d.feed),
      // output = c.getString("output").getOrElse(d.output),      
      // delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      // buffer = c.getInt("buffer").getOrElse(d.buffer),
      // throttle = c.getLong("throttle").getOrElse(d.throttle),
      // throttleSource = c.getLong("throttle.source").getOrElse(d.throttleSource),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    log.info(s"Config: ${config}")
    
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

    log.info(s"Store: ${store}")
    log.info(s"System prompt: ${config.sys}")
    
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
        
      case "prompt" | "responses" => 
        prompt(config.ai,config.params)(config)

      case "prompt-stream" | "stream" => 
        promptStream(config.ai,config.params)(config)

      case "messages" =>
        messages(config.ai, config.params)(config)

      case "messages-stream" =>
        messagesStream(config.ai, config.params)(config)
    }
    Console.err.println(s"${r}")
  }

  def ask(uri:String, params:Seq[String])(config:Config):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val aiUri = AiURI(uri)

    val provider:AiProvider = AiProvider(aiUri)    

    val (q0,max) = if(! params.isEmpty) {
      (params.mkString(" "),1)
    } else {
      val q0 = aiUri.prompt.getOrElse("")
      (q0,if(q0.isEmpty) Int.MaxValue else 1) 
    }

    val system = if(! config.sys.isEmpty)
      Some(config.sys)
    else
      aiUri.system

    log.info(s"q0 = '${q0}'")

    for (i <- 1 to max) {
      Console.err.print(s"${aiUri.getModel()}: ${i}> ")
      val q = if(q0.isEmpty && i < max)
         scala.io.StdIn.readLine()
      else
         q0

      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if(!q.isEmpty) {
        val p = provider.ask(q,aiUri.getModel(),system,images = config.images,outputType = aiUri.output)
        log.info(s"${p.get}")
        val txt = p.get.answer.get
        Console.err.println(s"${Console.RED}${aiUri.getModel()}${Console.YELLOW}: ${txt}${Console.RESET}")
      }
    }
  }

  def chat(uri:String, params:Seq[String])(config:Config):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val aiUri = AiURI(uri)

    log.info(s"uri = ${aiUri}")

    val provider:AiProvider = AiProvider(aiUri)
    
    val system = if(! config.sys.isEmpty)
      Some(config.sys)
    else
      aiUri.system

    val (q0,max) = if(! params.isEmpty) {
      (params.mkString(" "),1)
    } else {
      val q0 = aiUri.prompt.getOrElse("")
      (q0,if(q0.isEmpty) Int.MaxValue else 1) 
    }

    log.info(s"q0 = '${q0}'")

    val p0 = Chat(
      messages = Seq(
        ChatMessage("system",config.sys),
        ChatMessage("user",q0)
      )
    )

    var p = p0

    for (i <- 1 to max) {
      Console.err.print(s"${aiUri.getModel()}: [${p.messages.size}/${p.messages.map(_.content.size).sum}]:${i} > ")
      
      val q = if(q0.isEmpty && i < max)
         scala.io.StdIn.readLine()
      else
         q0

      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }

      if(!q.isEmpty) {
        
        val r = provider.chat(p.+(q), aiUri.getModel(),system,images = config.images,outputType = aiUri.output) 
        
        r match {
          case Success(p1) => 
            p = p1
          case Failure(e) => 
            Console.err.println(s"Error: ${e}")            
        }

        log.info(s"${r}")
        val txt = r.get.last.content
        Console.err.println(s"${Console.BLUE}${aiUri.getModel()}${Console.YELLOW}: ${txt}${Console.RESET}")
      }
    }
  }

  def prompt(uri:String, params:Seq[String])(config:Config):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val aiUri = AiURI(uri)

    val provider:AiProvider = AiProvider(aiUri)
    
    val system = if(! config.sys.isEmpty)
      Some(config.sys)
    else
      aiUri.system

    val (q0,max) = if(! params.isEmpty) {
      (params.mkString(" "),1)
    } else {
      val q0 = aiUri.prompt.getOrElse("")
      (q0,if(q0.isEmpty) Int.MaxValue else 1) 
    }
    
    val a0 = Ai(
      question = q0,
      model = aiUri.getModel(),
      xid = aiUri.tid
    )
    
    log.info(s"q0 = '${q0}'")

    var a = a0
    for (i <- 1 to max) {
      Console.err.print(s"${a.model}: ${a.xid}:${i} > ")
      
      val q = if(q0.isEmpty && i < max)
         scala.io.StdIn.readLine()
      else
         q0

      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if(!q.isEmpty) {
        val a1 = provider.prompt(a.copy(question = q),system,images = config.images,outputType = aiUri.output)
        log.info(s"${a1.get}")
        val txt = a1.get.answer.getOrElse("")
        Console.err.println(s"${Console.GREEN}${a1.get.model}${Console.YELLOW}: ${txt}${Console.RESET}")
        a = a1.get
      }
    }
  }
    

  def promptStream(uri:String, params:Seq[String])(config:Config):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val aiUri = AiURI(uri)

    val provider:AiProvider = AiProvider(aiUri)
    
    val system = if(! config.sys.isEmpty)
      Some(config.sys)
    else
      aiUri.system

    val (q0,max) = if(! params.isEmpty) {
      (params.mkString(" "),1)
    } else {
      val q0 = aiUri.prompt.getOrElse("")
      (q0,if(q0.isEmpty) Int.MaxValue else 1) 
    }

    val a0 = Ai(
      question = q0,
      model = aiUri.getModel(),
      xid = aiUri.tid
    )

    log.info(s"q0 = '${q0}'")

    var a = a0

    for (i <- 1 to max) {
      Console.err.print(s"${a.model}/${a.xid}:${i} > ")
      
      val q = if(q0.isEmpty && i < max)
         scala.io.StdIn.readLine()
      else
         q0

      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }

      if(!q.isEmpty) {
        
        val a1 = provider.promptStream(a.copy(question = q),
          (s:String) => {
            s match {
              // Pattern with item_id as number (allows optional trailing fields)
              case s"""data: {"type":"response.output_text.delta","item_id":${id},"output_index":${i},"content_index":${ic},"delta":"${txt}"""" =>
                log.info(s"${Console.BLUE}${txt}${Console.RESET}")

              // Flexible pattern using regex to extract delta from JSON with optional trailing fields
              case s if s.startsWith("""data: {"type":"response.output_text.delta"""") =>
                // Extract delta value using regex, allowing for optional trailing fields
                // Pattern matches: "delta":"<value>" where value can contain escaped quotes
                val deltaPattern = """"delta":"((?:[^"\\]|\\.)*)"""".r
                deltaPattern.findFirstMatchIn(s).foreach { m =>
                  val txt = m.group(1).replace("\\\"", "\"").replace("\\\\", "\\")
                  log.info(s"${Console.BLUE}${txt}${Console.RESET}")
                }

              case _ => 
                // ignore
                //Console.err.println(s"${s}")
            }
          },
          system,
          images = config.images,
          outputType = aiUri.output
        )

        log.info(s"${a1.get}")
        val txt = a1.get.answer.getOrElse("???")
        Console.err.println(s"${Console.GREEN}${a1.get.model}${Console.YELLOW}/${a1.get.xid}: ${txt}${Console.RESET}")
        a = a1.get
      }
    }
  }

  def messages(uri: String, params: Seq[String])(config: Config): Unit = {
    val aiUri = AiURI(uri)
    val provider: AiProvider = AiProvider(aiUri)
    val system =
      if (!config.sys.isEmpty) Some(config.sys)
      else aiUri.system
    val (q0, max) =
      if (!params.isEmpty) (params.mkString(" "), 1)
      else {
        val q0 = aiUri.prompt.getOrElse("")
        (q0, if (q0.isEmpty) Int.MaxValue else 1)
      }
    val a0 = Ai(
      question = q0,
      model = aiUri.getModel(),
      xid = aiUri.tid
    )
    log.info(s"q0 = '${q0}'")
    var a = a0
    for (i <- 1 to max) {
      Console.err.print(s"${a.model}: ${a.xid}:${i} (messages) > ")
      val q =
        if (q0.isEmpty && i < max) scala.io.StdIn.readLine()
        else q0
      if (q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if (!q.isEmpty) {
        val a1 = provider.messages(a.copy(question = q), system, images = config.images, outputType = aiUri.output)
        log.info(s"${a1.get}")
        val txt = a1.get.answer.getOrElse("")
        Console.err.println(s"${Console.GREEN}${a1.get.model}${Console.YELLOW}: ${txt}${Console.RESET}")
        a = a1.get
      }
    }
  }

  def messagesStream(uri: String, params: Seq[String])(config: Config): Unit = {
    val aiUri = AiURI(uri)
    val provider: AiProvider = AiProvider(aiUri)
    val system =
      if (!config.sys.isEmpty) Some(config.sys)
      else aiUri.system
    val (q0, max) =
      if (!params.isEmpty) (params.mkString(" "), 1)
      else {
        val q0 = aiUri.prompt.getOrElse("")
        (q0, if (q0.isEmpty) Int.MaxValue else 1)
      }
    val a0 = Ai(
      question = q0,
      model = aiUri.getModel(),
      xid = aiUri.tid
    )
    log.info(s"q0 = '${q0}'")
    var a = a0
    for (i <- 1 to max) {
      Console.err.print(s"${a.model}/${a.xid}:${i} (messages-stream) > ")
      val q =
        if (q0.isEmpty && i < max) scala.io.StdIn.readLine()
        else q0
      if (q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if (!q.isEmpty) {
        val a1 = provider.messagesStream(
          a.copy(question = q),
          (s: String) => {
            s match {
              case s if s.startsWith("data:") =>
                val data = s.stripPrefix("data:").trim
                scala.util.Try(spray.json.JsonParser(data)) match {
                  case scala.util.Success(js: spray.json.JsObject) =>
                    js.fields.get("type") match {
                      case Some(spray.json.JsString("content_block_delta")) =>
                        js.fields.get("delta").foreach {
                          case d: spray.json.JsObject =>
                            d.fields.get("type") match {
                              case Some(spray.json.JsString("text_delta")) =>
                                d.fields.get("text") match {
                                  case Some(spray.json.JsString(chunk)) =>
                                    log.info(s"${Console.BLUE}${chunk}${Console.RESET}")
                                  case _ =>
                                }
                              case _ =>
                            }
                          case _ =>
                        }
                      case _ =>
                    }
                  case _ =>
                }
              case _ =>
            }
          },
          system,
          images = config.images,
          outputType = aiUri.output
        )
        log.info(s"${a1.get}")
        val txt = a1.get.answer.getOrElse("???")
        Console.err.println(s"${Console.GREEN}${a1.get.model}${Console.YELLOW}/${a1.get.xid}: ${txt}${Console.RESET}")
        a = a1.get
      }
    }
  }
}
