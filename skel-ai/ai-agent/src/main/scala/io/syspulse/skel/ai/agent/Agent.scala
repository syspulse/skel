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
import io.cequence.openaiscala.domain.response.DeleteResponse

trait AgentFunction {
  protected val log = Logger(getClass)
  
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

  def getModel(): String = ModelId.gpt_4o

  def getInstructions(): String
  
  def getId(): Option[String]

  def delete(): Future[DeleteResponse]
    
  def getFunctions(): Map[String, AgentFunction]

  def ask(question:String, instructions:Option[String] = None, metadata:Option[Map[String,String]] = None): Future[Try[Seq[ThreadFullMessage]]] 
  
}
