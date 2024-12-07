package io.syspulse.skel.ai.agent

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import play.api.libs.json.Json
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.wsclient.service.CloseableService
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.JsValue

import io.cequence.wsclient.service.PollingHelper

trait Function {
  def run(args: JsValue): JsValue
}

object ExtAgent extends PollingHelper {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val adapters = OpenAIServiceAdapters.forFullService
  protected val service: OpenAIService =
    adapters.log(
      OpenAIServiceFactory(
        apiKey = sys.env.getOrElse("OPENAI_API_KEY",""),
        orgId = None
      ),
      "ext-agent",
      println(_) // simple logging
    )
  
  val model = 
    ModelId.gpt_4o
    //ModelId.gpt_3_5_turbo

  private def createAssistant() =
    for {
      assistant <- service.createAssistant(
        model = model,
        name = Some("ExtAgent"),
        instructions = Some(
          """
          You are a Contracts monitoring bot. Use the provided functions (addMonitoring, addContract) to answer questions.
          Always provide report about the actions you have taken with contract addresses and contract identifiers in the last message.
          """
        ),
        tools = tools
      )
    } yield assistant

  val tools: Seq[FunctionTool] = Seq(
    FunctionTool(
      name = "addMonitoring",
      description = Some("Adds new monitoring capabilities to the contract like Security Monitoring, Compliance Monitoring, Financial Monitoring, etc."),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "contractId" -> Map(
            "type" -> "string",
            "description" -> "Contract identifier returned by addContract function"
          ),
          "monitoring" -> Map(
            "type" -> "string",
            "enum" -> Seq("Security Monitoring", "Compliance Monitoring"),
            "description" -> "The type of monitoring to add to the contract"
          ),          
        ),
        "required" -> Seq("contractId","monitoring")
      )
    ),
    FunctionTool(
      name = "deleteContract",
      description = Some("Delete existing by  contractId)"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "contractId" -> Map(
            "type" -> "string",
            "description" -> "Contract identifier"
          ),
        ),
        "required" -> Seq("contractId")
      )
    ), 
    FunctionTool(
      name = "addContract",
      description = Some("Adds new contract to the project for monitoring and returns contract identifier (contractId)"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address of the contract in Ethereum format. For example: 0x742d35Cc6634C0532925a3b844f13573377189aF"
          ),
          "network" -> Map(
            "type" -> "string",
            "enum" -> Seq("Ethereum", "Arbitrum", "Optimism", "Base", "Polygon", "BNB Chain", "Solana", "Bitcoin", "Other"),
            "description" -> "The network where the contract is deployed on"
          ),
          "name" -> Map(
            "type" -> "string",
            "description" -> "Name of the contract. If not provided, the address will be used as the name."
          ),
        ),
        "required" -> Seq("address","network","name")
      )
    ),
  )
  
  def createSpecMessagesThread(question: String,userId: Option[String]): Future[Thread] =
    for {
      thread <- service.createThread(
        messages = Seq(
          ThreadMessage(question)
        ),
        metadata = Map("user_id" -> userId.getOrElse("ext-user"))
      )
      _ = println(thread)
    } yield thread


  def processRun(run: Run): Future[Run] = {
    println(s"processRun: <- ${run.status}: required_action: ${run.required_action}")
    if(run.required_action.isEmpty) {
      return Future.failed(new IllegalStateException(s"Run with id ${run.id} has no required action."))
    }

    val toolCalls = run.required_action.get.submit_tool_outputs.tool_calls    
    println(s"toolCalls: ${toolCalls}")

    val functionCalls = toolCalls.collect {
      case toolCall if toolCall.function.isInstanceOf[FunctionCallSpec] => toolCall
    }

    println(s"functionCalls: ---> ${functionCalls}")
    
    val available_functions: Map[String, Function] = Map(
      "addMonitoring" -> new AddMonitoring,
      "addContract" -> new AddContract,
      "deleteContract" -> new DeleteContract
    )
    
    val toolMessages = functionCalls.map { toolCall =>
      val functionCallSpec = toolCall.function
      val functionName = functionCallSpec.name
      val functionArgsJson = Json.parse(functionCallSpec.arguments)
      val functionResponse = available_functions.get(functionName) match {
        case Some(functionToCall) =>
          println(s"Function call -> '$functionName'")
          functionToCall.run(functionArgsJson)

        case _ =>
          Json.obj("error" -> s"Function $functionName not found")
      }
      AssistantToolOutput(
        output = Option(functionResponse.toString),
        tool_call_id = toolCall.id
      )
    }
    
    service.submitToolOutputs(
      run.thread_id,
      run.id,
      toolMessages,
      stream = false
    )
  }

  def run(question: String,userId: Option[String] = None): Future[_] = {
    try {
      for {
        assistant <- createAssistant()
        assistantId = assistant.id
        eventsThread <- createSpecMessagesThread(question,userId)

        _ <- service.listThreadMessages(eventsThread.id).map { messages =>
          println("messages:" + messages.map(_.content).mkString("\n"))
        }

        thread <- service.retrieveThread(eventsThread.id)
        _ = println(thread)

        run <- service.createRun(
          threadId = eventsThread.id,
          assistantId = assistantId,
          tools = tools,
          responseToolChoice = Some(ToolChoice.Required),
          settings = CreateRunSettings(),
          stream = false
        )

        _ = java.lang.Thread.sleep(5000)

        _ = println(s"retrieveRun: ->")

        runNew <- pollUntilDone((run: Run) => run.isFinished) {
          service
            .retrieveRun(run.thread_id, run.id)            
            .map(r => {
              //r.getOrElse(throw new IllegalStateException(s"Run with id ${run.id} not found."))
              r match {
                case Some(run) => 
                  processRun(run)
                  run
                case None => throw new IllegalStateException(s"Run with id ${run.id} not found.")
              }
              
            })
        }

        // updatedRunOpt <- service.retrieveRun(eventsThread.id, run.id).recoverWith {
        //   case e =>
        //     println(s"Error retrieving run: ${e}")
        //     Future.failed(e)
        // }        
        
        // updatedRun = updatedRunOpt.get
        
        // toolCalls = updatedRun.required_action.get.submit_tool_outputs.tool_calls
        
        // _ = println(s"toolCalls: ${toolCalls}")

        // functionCalls = toolCalls.collect {
        //   case toolCall if toolCall.function.isInstanceOf[FunctionCallSpec] => toolCall
        // }

        // _ = println(s"functionCalls: ---> ${functionCalls}")
        
        // available_functions: Map[String, Function] = Map(
        //   "addMonitoring" -> new AddMonitoring,
        //   "addContract" -> new AddContract,
        //   "deleteContract" -> new DeleteContract
        // )
        
        // toolMessages = functionCalls.map { toolCall =>
        //   val functionCallSpec = toolCall.function
        //   val functionName = functionCallSpec.name
        //   val functionArgsJson = Json.parse(functionCallSpec.arguments)
        //   val functionResponse = available_functions.get(functionName) match {
        //     case Some(functionToCall) =>
        //       println(s"Function call -> '$functionName'")
        //       functionToCall.run(functionArgsJson)

        //     case _ =>
        //       Json.obj("error" -> s"Function $functionName not found")
        //   }
        //   AssistantToolOutput(
        //     output = Option(functionResponse.toString),
        //     tool_call_id = toolCall.id
        //   )
        // }
        // _ <- service.submitToolOutputs(
        //   updatedRun.thread_id,
        //   updatedRun.id,
        //   toolMessages,
        //   stream = false
        // )
        // _ = java.lang.Thread.sleep(1000)

        finalMessages <- service.listThreadMessages(eventsThread.id)
      } yield {
        println("-"*100)
        //println(run)
        println(runNew)
        println("-"*100)        
        // runNew.required_action.get.submit_tool_outputs.tool_calls.foreach {
        //   toolCall =>
        //     println(s"Tool call: ${toolCall.id}")
        //     println(toolCall)
        // }
        println("="*100)
        println("Assistant answer:" + finalMessages.map(_.content).mkString("\n"))
      }
    } catch {
      case e: Throwable =>
        println(s"Error running: ${e}")
        Future.failed(e)
    }
  }

  // unit is ignored here
  class AddContract extends Function {
    def run(functionArgsJson: JsValue): JsValue = {
      val address = (functionArgsJson \ "address").as[String]
      val network = (functionArgsJson \ "network").as[String]
      val name = (functionArgsJson \ "name").asOpt[String]           
      val contractId = scala.util.Random.nextInt(1000)
      Json.obj(
        "address" -> address, 
        "network" -> network, 
        "name" -> name, 
        "contractId" -> contractId,
        //"action" -> s"Contract added: ${address} on ${network} with name ${name}"
      )
    }
  }

  class AddMonitoring extends Function {
    def run(functionArgsJson: JsValue): JsValue = {
      val contractId = (functionArgsJson \ "contractId").as[String]
      val monitoring = (functionArgsJson \ "monitoring").as[String]
      Json.obj(
        "monitoring" -> monitoring, 
        "contractId" -> contractId,
        //"action" -> s"Monitoring type ${monitoring} for contract ${contractId}"
      )
    }
  }

  class DeleteContract extends Function {
    def run(functionArgsJson: JsValue): JsValue = {
      val contractId = (functionArgsJson \ "contractId").as[String]
      Json.obj("contractId" -> contractId)
    }
  }
}
