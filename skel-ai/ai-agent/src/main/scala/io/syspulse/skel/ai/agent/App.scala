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

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/agent",

  datastore:String = "openai://",
  agent:String = "ext://",
          
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
        ArgString('a', "agent",s"Agent [ext://,weather://] (def: ${d.agent})"),
                               
        ArgCmd("server","Server"),
        ArgCmd("ask","Ask question"),
        ArgCmd("models","List models"),
        ArgCmd("asking","Ask question streamed"),
        ArgCmd("ext-agent","Run ext-agent"),
        ArgCmd("ext-agent-2","Run ext-agent-2"),
        ArgCmd("weather-agent","Run weather-agent"),
        ArgCmd("prompt","Run prompt"),
        
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
      agent = c.getString("agent").getOrElse(d.agent),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    implicit val ec = ExecutionContext.global
    implicit val materializer = Materializer(actor.ActorSystem())

    val (service,uri:OpenAiURI) = config.datastore.split("://").toList match {
      case "openai" :: _ => 
        val uri = OpenAiURI(config.datastore)
        val service = OpenAIServiceFactory.withStreaming(
          apiKey = uri.apiKey,
          orgId = uri.org
        )
        (service,uri)

      case _ => 
        Console.err.println(s"Unknown datastore: '${config.datastore}'")
        sys.exit(1)
    }

    val agent = config.agent.split("://").toList match {
      case "ext-agent" :: Nil =>
        new ExtAgent(uri)
      
      case "help-agent" :: Nil =>
        new HelpAgent(uri)
      
      case _ => 
        Console.err.println(s"Unknown agent: '${config.agent}'")
        sys.exit(1)      
    }

    Console.err.println(s"Service: ${service}")
    Console.err.println(s"Agent: ${agent}")
    
    val r = config.cmd match {      
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
        prompt(agent,config.params)

      case "ext-agent" =>
        new ExtAgent(uri).ask(
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

      case "ext-agent-2" =>
        new ExtAgent(uri).ask(
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
    Console.err.println(s"r = ${r}")
  }

  def prompt(agent:Agent, params:Seq[String]):Unit = {
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
        val f = agent.ask(q)
        val r = await(f)
        Console.err.println(s"${Console.RED}${agent.getName()}${Console.YELLOW}: ${r}${Console.RESET}")
      }
    }
  }
}
