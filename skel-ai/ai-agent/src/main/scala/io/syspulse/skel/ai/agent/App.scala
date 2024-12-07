package io.syspulse.skel.ai.agent

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.ActorSystem

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.jvm.uuid._

import io.syspulse.skel.FutureAwaitable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import io.syspulse.skel.ai._
import scala.concurrent.ExecutionContext
import akka.stream.scaladsl.Sink

import akka.stream.Materializer
import akka.actor
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
import io.cequence.openaiscala.domain.settings.CreateCompletionSettings
import io.cequence.openaiscala.domain.ModelId

import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/agent",

  datastore:String = "openai://",
          
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
      new ConfigurationArgs(args,"ai-agent","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [mem://,gl://,ofac://] (def: ${d.datastore})"),
                               
        ArgCmd("server","Server"),
        ArgCmd("ask","Ask question"),
        ArgCmd("models","List models"),
        ArgCmd("asking","Ask question streamed"),
        ArgCmd("ext-agent","Run ext-agent"),
        ArgCmd("weather-agent","Run weather-agent"),
        
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
            
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
    
    implicit val ec = ExecutionContext.global
    implicit val materializer = Materializer(actor.ActorSystem())

    val service = config.datastore.split("://").toList match {
      case "openai" :: Nil =>         
        val service = OpenAIServiceFactory.withStreaming(
          apiKey = sys.env.getOrElse("OPENAI_API_KEY",""),
          orgId = None
        )
        service

      case _ => 
        Console.err.println(s"Unknown datastore: '${config.datastore}'")
        sys.exit(1)      
    }

    Console.err.println(s"Service: ${service}")
    
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

      case "ext-agent" =>
        ExtAgent.run(
"""
I want to add Compliance Monitoring to my project Contracts. 
I have two contracts: 0x742d35Cc6634C0532925a3b844f13573377189aF and 0x1234567890123456789012345678901234567890. 
First contract is deployed on Ethereum and second on Arbitrum network.
Add contracts and add Compliance Monitoring to them.
"""

)
    }
    Console.err.println(s"r = ${r}")
  }
}
