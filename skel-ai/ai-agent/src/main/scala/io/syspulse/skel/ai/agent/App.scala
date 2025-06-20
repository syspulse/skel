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
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain._

import scala.io.StdIn
import play.api.libs.json.Json

import io.syspulse.skel.ext._
import io.syspulse.skel.ai.agent.blockchain.AgentBlockchain

import io.syspulse.skel.FutureAwaitable._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/agent",

  datastore:String = "",
  provider:String = "openai://",
  agent:String = "agent://ext-agent",
  meta:Seq[String] = Seq("pid=760"),
  instructions:String = "",

  serviceUrl:String = "http://localhost:8080/api/v1/ext",
  serviceToken:Option[String] = None,

  cmd:String = "memory",
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
        ArgString('_', "agent.instructions",s"Instructions (def: ${d.instructions})"),

        ArgString('_', "service.url",s"Service uri (def: ${d.serviceUrl})"),
        ArgString('_', "service.token",s"Service access token (def: ${d.serviceToken})"),

        ArgCmd("server","Server"),
        ArgCmd("ask","Ask question"),
        ArgCmd("models","List models"),
        ArgCmd("asking","Ask question streamed"),
        ArgCmd("ext-agent","Call ext-agent functions"),
        ArgCmd("ext-agent-ask","Run ext-agent-ask"),
        ArgCmd("ext-agent-ask2","Run ext-agent-ask2"),
        ArgCmd("evm-agent","Run evm-agent"),        

        ArgCmd("delete","Delete Agent"),

        ArgCmd("prompt","Run prompt"),
        ArgCmd("memory","Run prompt with memory"),

        ArgCmd("ext","Run Ext client"), //to work with Extractor API
        //ArgUnknown(),
        
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
      instructions = c.getString("agent.instructions").getOrElse(d.instructions),
      
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
      case "agent" :: "ext-agent" :: Nil => new AgentExt(uri,extClient)
      case "agent" :: "help-agent" :: Nil => new AgentHelp(uri)
      case "agent" :: "evm-agent" :: Nil => new AgentEvm(uri,extClient)
      case "agent" :: "token-agent" :: Nil => new AgentToken(uri,extClient)
      case "agent" :: "sec-agent" :: Nil => new AgentSec(uri,extClient)
      case "agent" :: "fw-agent" :: Nil =>  new AgentFw(uri,AgentFwConfig(config.instructions),extClient)
      case "agent" :: "jail-agent" :: Nil => new AgentJail(uri,extClient)
      case "agent" :: "blockchain-agent" :: Nil => new AgentBlockchain(uri)
      case ("agent" :: "prompt-agent" :: Nil) | ("prompt" :: Nil) => new AgentPrompt(uri)
      case "agent" :: "test-agent" :: Nil => new AgentTest(uri)
      
      
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
        val params = config.params.drop(1)

        config.params.head match {
          case "project-contracts" | "contracts" => 
            ext.getProjectContracts(params(0),params.drop(1).headOption.map(_.replaceAll("%20"," ")))
          
          case "contract-detectors" | "detectors" => 
            ext.getContractDetectors(params(0),params.drop(1).headOption.map(_.replaceAll("%20"," ")))

          case "detector-schema" =>
            ext.getDetectorSchemas(params.headOption.map(_.replaceAll("%20"," ")))

          case "detector-add" =>
            val conf:ujson.Obj = if(params.length > 6)
              ujson.read(params(6)).obj 
            else 
              ujson.Obj()

            ext.addDetector(
              pid = params(0),
              cid = params(1),
              did = params(2).replaceAll("%20"," "),
              name = params(3),  
              tags = params.drop(4).headOption.getOrElse("COMPLIANCE").split(",").toSeq,
              sev = if(params.length > 5) params.drop(5).head else "AUTO",
              conf = conf,
            )
          case "detector-del" =>            
            ext.delDetector(
              detectorId = params(0),
            )

          case "contract" => 
            ext.getContract(params(0).toInt)

          case "contract-call" =>
            ext.callContract(
              addr = params(0),
              network = params(1),
              func = params(2),
              params = params.drop(3),
            )

          case "alert" | "event" =>
            ext.alert(
              cid = params(0).toInt,
              did = params(1),
              addr = params.drop(2).headOption,
              network = params.drop(3).headOption,
            )

          case _ =>
            Console.err.println(s"Unknown ext command: '${config.params.head}'")
            sys.exit(1)
        }

      case "ext-agent" =>
        class AgentExtTest extends AgentExt(uri,extClient) {          
        }

        val agent = new AgentExtTest()

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

      case "evm-agent" =>
        class AgentEvmTest extends AgentEvm(uri,extClient) {          
        }
        val agent = new AgentEvmTest()        

        config.params.head match {
          case "callContract" =>
            val params = config.params.drop(1)
            agent.getFunctions().get("callContract").get.run(
              Json.obj(
                "address" -> params(1),
                "functionName" -> params(2),
                "functionParams" -> params.drop(3),
              ),
              metadata = Map("pid" -> params(0))
            )
          case _ =>
            Console.err.println(s"Unknown evm command: '${config.params.head}'")
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
      
      case "help-agent" =>
        new AgentHelp(uri).ask(
          "What is Extractor?"
        )

      case "delete" =>
        agent.delete().await()

      case "prompt" =>
        prompt(
          agent,
          config.params,
          config.meta.map(m => m.split("=").toList match { case k :: v :: Nil => k -> v }).toMap
        )

      case "memory" =>
        promptMemory(
          agent,
          config.params,
          config.meta.map(m => m.split("=").toList match { case k :: v :: Nil => k -> v }).toMap
        )
      

      case "ext-agent-ask" =>
        new AgentExt(uri,extClient).ask(
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
        new AgentExt(uri,extClient).ask(
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

  def promptMemory(agent:Agent, params:Seq[String], metadata0:Map[String,String]):Unit = {
    import io.syspulse.skel.FutureAwaitable._

    var metadata = metadata0
    var thid = ""
    var aid = agent.getId().getOrElse("")

    val q0 = params.mkString(" ")
    Console.err.println(s"q0 = '${q0}'")    
    Console.err.println(s"thread_id = '${thid}'")
    Console.err.println(s"aid = '${aid}'")

    for (i <- 1 to Int.MaxValue) {
      Console.err.print(s"${agent.getModel()} / ${aid} / ${thid} / ${i}> ")
      val q = StdIn.readLine()
      if(q == null || q.trim.toLowerCase() == "exit") {
        sys.exit(0)
      }
      if(!q.isEmpty) {
        val f = agent.ask(q,metadata=Some(metadata))
        val r = await(f)
        
        Console.err.println(s"${r}")
        val txt = r.get.head.content.last.text
        aid = agent.getId().getOrElse("")
        
        Console.err.println(s"${Console.GREEN}${agent.getName()}${Console.YELLOW}: ${txt}${Console.RESET}")

        // save thread_id
        val threadId = r.get.head.thread_id
        metadata = metadata + ("thread_id" -> threadId)
        thid = threadId
      }
    }
  }
  
}
