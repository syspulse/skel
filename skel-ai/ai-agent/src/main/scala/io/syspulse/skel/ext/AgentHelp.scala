package io.syspulse.skel.ext

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
import io.cequence.openaiscala.domain.ModelId
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.JsValue
import io.syspulse.skel.ai.core.openai.OpenAiURI
import io.syspulse.skel.ai.agent.AgentFunction
import io.syspulse.skel.ai.agent.AgentFile

class AgentHelp(val uri:OpenAiURI) extends AgentFile {
  
  if(! uri.vdb.isDefined)
    throw new IllegalArgumentException("vectorStoreId is required")

  def getName(): String = "help-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o)
    //ModelId.gpt_3_5_turbo
  
  def getInstructions(): String = 
    """
    You are an assistant that helps me find Extractor product information and functionality description.
    """

  def getVectorStoreId(): String = uri.vdb.get

  // no functions for FileSearchTool
  def getFunctions(): Map[String, AgentFunction] = Map()
}
