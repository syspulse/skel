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
import scala.util.{Try,Success,Failure}
import io.syspulse.skel.ai.core.openai.OpenAiURI
import io.cequence.openaiscala.domain.response.Assistant

trait AgentFunction {
  def run(args: JsValue, metadata:Map[String,String]): JsValue
}

trait Agent extends PollingHelper {
  protected val log = Logger(getClass)

  // polling interval in milliseconds
  override protected val pollingMs = 550
  def uri:OpenAiURI

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  protected val adapters = OpenAIServiceAdapters.forFullService
  protected val service: OpenAIService = createService(uri)
  
  def createService(uri:OpenAiURI): OpenAIService =
    adapters.log(
      OpenAIServiceFactory(
        apiKey = uri.apiKey,
        orgId = uri.org,        
      ),
      getName(),
      log.info(_) // simple logging
    )
  
  def getName(): String

  def getModel(): String = 
    ModelId.gpt_4o
    //ModelId.gpt_3_5_turbo

  def getInstructions(): String

  @volatile
  var assistant:Option[Assistant] = None

  protected def createAssistant(instructions: String): Future[Assistant] = {
    uri.aid match {
      case Some(aid) =>
        log.info(s"retrieving assistant: '${aid}'")        
        service.retrieveAssistant(aid).map(_.get)
      case None =>
        for {
          ass <- service.createAssistant(
            model = getModel(),
            name = Some(getName()),
            instructions = Some(instructions),          
            tools = getTools()
          )      
        } yield ass
      }
  }

  // create or get existing assistant
  protected def createOrGetAssistant(instructions: String): Future[Assistant] = {
    if(assistant.isDefined) {
      return Future.successful(assistant.get)
    }

    for {
      ass <- createAssistant(instructions)

      // memoize 
      _ = assistant = Some(ass)
    } yield ass
  }

  protected def getTools(): Seq[AssistantTool]
  
  protected def createSpecMessagesThread(question: String,metadata:Option[Map[String,String]]): Future[Thread] =
    for {
      thread <- service.createThread(
        messages = Seq(
          ThreadMessage(question)
        ),
        metadata = metadata.getOrElse(Map.empty)
      )
      _ = log.info(s"thread: ${thread}")
    } yield thread

  def getFunctions(): Map[String, AgentFunction]

  def processRun(run: Run, thread: Thread): Future[Run] = {
    log.info(s"processRun: status=${run.status}: required_action=${run.required_action}")
    
    if(run.status == RunStatus.Completed) {
      // keep polling
      log.info(s"Completed")
      return Future.successful(run)
    }

    if(run.status == RunStatus.InProgress) {
      // keep polling
      log.info(s"waiting for state change...")
      return Future.failed(new IllegalStateException(s"polling"))
    }

    if(! run.required_action.isDefined) {
      log.warn(s"Invalid state: ${run.id}: status=${run.status}: ${run.required_action}")
      // this is expected until statu == InProgress
      return Future.failed(new IllegalStateException(s"Run ${run.id}: no required action"))
    }

    val toolOutputs = run.required_action.get.submit_tool_outputs
    val toolCalls = toolOutputs.tool_calls
    log.info(s"Processing Run: toolCalls=${toolCalls}")
    //val toolCalls = run.required_action.get.submit_tool_outputs.tool_calls    

    val functionCalls = toolCalls.collect {
      case toolCall if toolCall.function.isInstanceOf[FunctionCallSpec] => toolCall
    }

    log.debug(s"functionCalls: --> ${functionCalls}")

    val metadata = thread.metadata

    val available_functions: Map[String, AgentFunction] = getFunctions()
  
    val toolMessages = functionCalls.map { toolCall =>
      val functionCallSpec = toolCall.function
      val functionName = functionCallSpec.name
      val functionArgsJson = Json.parse(functionCallSpec.arguments)
      val functionResponse = available_functions.get(functionName) match {
        case Some(functionToCall) =>
          log.info(s"Function call --> $functionName(${functionArgsJson},${metadata})")
          try {
            // execute function
            functionToCall.run(functionArgsJson,metadata)
          } catch {
            case e: Throwable =>
              log.error(s"Function failed: ${functionName}", e)
              Json.obj(
                "error" -> "Failed due to Function Call Error",
                "error_description" -> e.getMessage,
              )
          }

        case _ =>
          log.error(s"Function call: not found: '$functionName'")
          Json.obj("error" -> s"Function not found: ${functionName}")
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
  
  def ask(question:String, instructions:Option[String] = None, metadata:Option[Map[String,String]] = None): Future[Try[Seq[ThreadFullMessage]]] = {    
    val ff = for {
        assistant <- createOrGetAssistant(instructions.getOrElse(getInstructions()))
        _ = log.info(s"assistant: ${assistant}")

        assistantId = assistant.id
        eventsThread <- createSpecMessagesThread(question,metadata)

        _ <- service.listThreadMessages(eventsThread.id).map { messages =>
          log.info("messages: =============\n" + messages.map(_.content).mkString("\n"))
        }

        //thread <- service.retrieveThread(eventsThread.id)
        thread <- Future.successful(eventsThread)
        _ = log.info(s"thread: ${thread}")
        
        run0 <- service.createRun(
          threadId = eventsThread.id,
          assistantId = assistantId,
          tools = getTools(),          
          // responseToolChoice = Some(ToolChoice.Required),
          settings = 
            CreateRunSettings(),
            // CreateRunSettings(
            //   model = Some(getModel()),
            //   temperature = Some(0.5),
            // ),
          stream = false
        )

        _ = log.info(s"retrieveRun: -> ${run0.thread_id}/${run0.id}")

        run1 <- pollUntilDone((run: Run) => run.isFinished) {
          service
            .retrieveRun(run0.thread_id, run0.id)            
            .map(r => {
              //r.getOrElse(throw new IllegalStateException(s"Run with id ${run.id} not found."))
              r match {
                case Some(run) => 
                  processRun(run,eventsThread)
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
