package io.syspulse.skel.ai.agent

import com.typesafe.scalalogging.Logger

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
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait AiFunction {
  def run(args: JsValue): JsValue
}

trait Agent extends PollingHelper {
  protected val log = Logger(getClass)

  // polling interval in milliseconds
  override protected val pollingMs = 550

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  protected val adapters = OpenAIServiceAdapters.forFullService
  protected val service: OpenAIService =
    adapters.log(
      OpenAIServiceFactory(
        apiKey = sys.env.getOrElse("OPENAI_API_KEY",""),
        orgId = None
      ),
      getName(),
      log.info(_) // simple logging
    )
  
  def getName(): String

  def getModel() = 
    ModelId.gpt_4o
    //ModelId.gpt_3_5_turbo

  def getInstructions(): String

  private def createAssistant(instructions: String) =
    for {
      assistant <- service.createAssistant(
        model = getModel(),
        name = Some(getName()),
        instructions = Some(instructions),          
        tools = getTools()
      )
    } yield assistant

  def getTools(): Seq[FunctionTool]
  
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

  def getFunctions(): Map[String, AiFunction]

  def processRun(run: Run): Future[Run] = {
    log.info(s"processRun: status=${run.status}: required_action=${run.required_action}")
    if(run.required_action.isEmpty) {
      return Future.failed(new IllegalStateException(s"Run ${run.id}: no required action"))
    }

    val toolCalls = run.required_action.get.submit_tool_outputs.tool_calls    
    log.debug(s"toolCalls: ${toolCalls}")

    val functionCalls = toolCalls.collect {
      case toolCall if toolCall.function.isInstanceOf[FunctionCallSpec] => toolCall
    }

    log.debug(s"functionCalls: --> ${functionCalls}")
    
    val available_functions: Map[String, AiFunction] = getFunctions()
    
    val toolMessages = functionCalls.map { toolCall =>
      val functionCallSpec = toolCall.function
      val functionName = functionCallSpec.name
      val functionArgsJson = Json.parse(functionCallSpec.arguments)
      val functionResponse = available_functions.get(functionName) match {
        case Some(functionToCall) =>
          log.info(s"Function call --> $functionName(${functionArgsJson})")
          functionToCall.run(functionArgsJson)

        case _ =>
          log.error(s"Function call: not found: '$functionName'")
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
  
  def ask(question:String, instructions:Option[String] = None, userId:Option[String] = None): Future[Try[Seq[ThreadFullMessage]]] = {    
    val ff = for {
        assistant <- createAssistant(instructions.getOrElse(getInstructions()))
        _ = log.info(s"assistant: ${assistant}")

        assistantId = assistant.id
        eventsThread <- createSpecMessagesThread(question,userId)

        _ <- service.listThreadMessages(eventsThread.id).map { messages =>
          log.debug("messages:" + messages.map(_.content).mkString("\n"))
        }

        thread <- service.retrieveThread(eventsThread.id)
        _ = log.info(s"thread: ${thread}")

        run0 <- service.createRun(
          threadId = eventsThread.id,
          assistantId = assistantId,
          tools = getTools(),
          responseToolChoice = Some(ToolChoice.Required),
          settings = CreateRunSettings(),
          stream = false
        )

        // _ = java.lang.Thread.sleep(5000)

        _ = log.info(s"retrieveRun: -> ${run0.thread_id}/${run0.id}")

        run1 <- pollUntilDone((run: Run) => run.isFinished) {
          service
            .retrieveRun(run0.thread_id, run0.id)            
            .map(r => {
              //r.getOrElse(throw new IllegalStateException(s"Run with id ${run.id} not found."))
              r match {
                case Some(run) => 
                  processRun(run)
                  run
                case None => throw new IllegalStateException(s"Run ${run0.id}: not found")
              }
            })
        }        

        finalMessages <- service.listThreadMessages(eventsThread.id)
      } yield {        
        log.info("Assistant answer:\n" + finalMessages.map(_.content).mkString("\n"))
        (assistant,thread,run0,run1,finalMessages)
      }
    
    ff
    .map{ 
      case (_,_,_,_,finalMessages) => Success(finalMessages)
      case _ => Failure(new IllegalStateException("Failed to get final messages"))
    }
    .recover {    
      case e: Throwable =>
        log.error(s"failed to run Assistant",e)
        Failure(e)
    }
  }
  
}
