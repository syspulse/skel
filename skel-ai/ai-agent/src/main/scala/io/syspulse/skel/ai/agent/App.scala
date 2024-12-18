package io.syspulse.skel.ai.agent

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.Materializer
import akka.actor
import java.util.concurrent.TimeUnit

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.ai.core.openai.OpenAiURI

import io.jvm.uuid._

import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
import io.cequence.openaiscala.domain.settings.CreateCompletionSettings
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain._
import scala.io.StdIn
import play.api.libs.json.Json

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/agent",

  datastore:String = "",
  provider:String = "openai://",
  agent:String = "agent://ext-agent",
  meta:Seq[String] = Seq("pid=898"),

  serviceUrl:String = "http://localhost:8080/api/v1/ext",
  serviceToken:Option[String] = None,

  cmd:String = "prompt",
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
      new ConfigurationArgs(args,"ai-agent","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [mem://,gl://,ofac://] (def: ${d.datastore})"),
        ArgString('s', "provider",s"Provider [openai://,ollama://] (def: ${d.provider})"),
        ArgString('a', "agent",s"Agent [ext://,weather://] (def: ${d.agent})"),
        ArgString('m', "meta",s"Metadata list (def: ${d.meta.mkString(",")})"),

        ArgString('_', "service.url",s"Service uri (def: ${d.serviceUrl})"),
        ArgString('_', "service.token",s"Service access token (def: ${d.serviceToken})"),

        ArgCmd("server","Server"),
        ArgCmd("ask","Ask question"),
        ArgCmd("models","List models"),
        ArgCmd("asking","Ask question streamed"),
        ArgCmd("ext-agent","Call ext-agent functions"),
        ArgCmd("ext-agent-ask","Run ext-agent-ask"),
        ArgCmd("ext-agent-ask2","Run ext-agent-ask2"),
        ArgCmd("weather-agent","Run weather-agent"),
        ArgCmd("prompt","Run prompt"),

        ArgCmd("ext","Run Ext client"), //to work with Extractor API
        
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
      provider = c.getString("provider").getOrElse(d.provider),
      agent = c.getString("agent").getOrElse(d.agent),
      meta = c.getListString("meta",d.meta),

      serviceUrl = c.getString("service.url").getOrElse(d.serviceUrl),
      serviceToken = c.getString("service.token").orElse(d.serviceToken),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    implicit val ec = ExecutionContext.global
    implicit val materializer = Materializer(actor.ActorSystem())

    val (service,uri:OpenAiURI) = config.provider.split("://").toList match {
      case "openai" :: _ => 
        val uri = OpenAiURI(config.provider)
        val service = OpenAIServiceFactory.withStreaming(
          apiKey = uri.apiKey,
          orgId = uri.org
        )
        (service,uri)

      case _ => 
        Console.err.println(s"Unknown datastore: '${config.provider}'")
        sys.exit(1)
    }

    val extClient = new ExtClient(config.serviceUrl,config.serviceToken)

    val agent = config.agent.split("://").toList match {
      case "agent" :: "ext-agent" :: Nil =>
        new ExtAgent(uri,extClient)
      
      case "agent" :: "help-agent" :: Nil =>
        new HelpAgent(uri)

      case ("agent" :: "prompt-agent" :: Nil) | ("prompt" :: Nil) =>
        new PromptAgent(uri)
      
      
      // resolve Agent
      // case "agent" :: uri  =>
        
      case _ => 
        Console.err.println(s"Unknown agent: '${config.agent}'")
        sys.exit(1)      
    }

    Console.err.println(s"Provider: ${config.provider}")
    Console.err.println(s"Service: ${service}")
    Console.err.println(s"Agent: ${agent}")
    Console.err.println(s"Service: ${extClient}")
    
    val r = config.cmd match {
      case "ext" =>
        val ext = new ExtClient(config.serviceUrl,config.serviceToken)
        config.params.head match {
          case "project-contracts" | "contracts" => 
            ext.getProjectContracts(config.params.drop(1).head,config.params.drop(2).headOption)
          
          case "detector-schema" =>
            ext.getDetectorSchemas(config.params.drop(1).headOption)

          case "detector-add" =>
            val conf:ujson.Obj = if(config.params.length > 7)
              ujson.read(config.params(7)).obj 
            else 
              ujson.Obj()

            ext.addDetector(
              pid = config.params(1),
              cid = config.params(2),
              did = config.params(3).replaceAll("%20"," "),
              name = config.params(4),  
              tags = config.params.drop(5).headOption.getOrElse("COMPLIANCE").split(",").toSeq,
              sev = if(config.params.length > 6) config.params.drop(6).head else "AUTO",
              conf = conf,
            )
          case "detector-del" =>            
            ext.delDetector(
              detectorId = config.params(1),
            )
          case _ =>
            Console.err.println(s"Unknown ext command: '${config.params.head}'")
            sys.exit(1)
        }

      case "ext-agent" =>
        class TestAgent extends ExtAgent(uri,extClient) {          
        }

        val agent = new TestAgent()

        config.params.head match {
          case "addContract" =>
            agent.getFunctions().get("addContract").get.run(
              Json.obj(
                "address" -> config.params(2),
                "network" -> config.params(3),
                "name" -> config.params(4),
              ),
              metadata = Map("pid" -> config.params(1))
            )
          
          case "addMonitoringType" =>
            agent.getFunctions().get("addMonitoringType").get.run(
              Json.obj(
                "address" -> config.params(2),
                "monitoringType" -> config.params(3),                
              ),
              metadata = Map("pid" -> config.params(1))
            )

          case "deleteContract" =>
            agent.getFunctions().get("deleteContract").get.run(
              Json.obj(
                "address" -> config.params(2),                
              ),
              metadata = Map("pid" -> config.params(1))
            )

          case "getProjectContracts" =>
            agent.getFunctions().get("getProjectContracts").get.run(
              Json.obj(
                "address" -> config.params.drop(2).headOption
              ),
              metadata = Map("pid" -> config.params(1))
            )

          case _ =>
            Console.err.println(s"Unknown ext command: '${config.params.head}'")
            sys.exit(1)
        }

      case "models" =>
        service.listModels.map(models =>
          Console.err.println(s"Models: ${models.mkString("\n")}")
          //models.foreach(println)
        ).recoverWith(e => {
          Console.err.println(s"failed to list models: ${e}")
          Future.failed(e)
        })

      case "ask" => 
        val txt = config.params.mkString(" ")        
        Console.err.println(s"q = '${txt}'")

        service.createCompletion(txt).map(c =>
          Console.err.println(s"${c}")
          //models.foreach(println)
        ).recoverWith(e => {
          Console.err.println(s"failed to ask completion: ${e}")
          Future.failed(e)
        })
    
      case "asking" => 
        val txt = config.params.mkString(" ")        
        Console.err.println(s"q = '${txt}'")
        val source = service.createChatCompletionStreamed(
          messages = Seq(
            UserMessage(txt)  // Use the actual input text instead of hardcoded prompt
          ),
          settings = CreateChatCompletionSettings(
            model = ModelId.gpt_3_5_turbo,
            temperature = Some(0.9),
            presence_penalty = Some(0.2),
            frequency_penalty = Some(0.2)
          )
        )

      source.map(completion => 
        Console.err.println(completion.choices.head.delta.content)
      ).runWith(Sink.ignore)

      case "weather-agent" =>
        WeatherAgent.run()

      case "help-agent" =>
        new HelpAgent(uri).ask(
          "What is Extractor?"
        )
      
      case "prompt" =>
        prompt(agent,config.params,config.meta.map(m => m.split("=").toList match { case k :: v :: Nil => k -> v }).toMap)

      case "ext-agent-ask" =>
        new ExtAgent(uri,extClient).ask(
"""
I want to add Compliance Monitoring to my project Contracts. 
I have two contracts: 0x742d35Cc6634C0532925a3b844f13573377189aF and 0x1234567890123456789012345678901234567890. 
First contract is deployed on Ethereum and second on Arbitrum network.
Add contracts and add Compliance Monitoring to them.
""",

// Some("""
// You are a Contracts monitoring bot. Use the provided functions (addMonitoring, addContract) to answer questions.
// Always provide report about the actions you have taken with contract addresses and contract identifiers in the last message.
// """),

Some("""
You are a Contracts monitoring bot. Use the provided functions (addMonitoring, addContract) to answer questions.
If question was related to adding, deleting or setting monitoring for contracts, provide report about the actions you have taken with contract addresses and contract identifiers in the last message.
Otherwise, just answer the question.
"""),

)

      case "ext-agent-ask2" =>
        new ExtAgent(uri,extClient).ask(
"""
Tell me about Ethereum network in one sentence.
""",
Some("""
You are a Contracts monitoring bot. Use the provided functions (addMonitoring, addContract) to answer questions.
If question was related to adding, deleting or setting monitoring for contracts, provide report about the actions you have taken with contract addresses and contract identifiers in the last message.
Otherwise, just answer the question.
"""),
)  
    }
    Console.out.println(s"${r}")
  }

  def prompt(agent:Agent, params:Seq[String], metadata:Map[String,String]):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    val q0 = params.mkString(" ")
    Console.err.println(s"q0 = '${q0}'")

    for (i <- 1 to Int.MaxValue) {
      Console.err.print(s"${i}> ")
      val q = StdIn.readLine()
      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if(!q.isEmpty) {
        val f = agent.ask(q,metadata=Some(metadata))
        val r = await(f)
        Console.err.println(s"${r}")
        val txt = r.get.head.content.head.text.get.value
        Console.err.println(s"${Console.RED}${agent.getName()}${Console.YELLOW}: ${txt}${Console.RESET}")
      }
    }
  }
}
