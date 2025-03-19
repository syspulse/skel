package io.syspulse.skel.ext

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.openaiscala.domain.ModelId
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.JsValue
import play.api.libs.json.Writes
import play.api.libs.json.Json

import io.syspulse.skel.ai.core.openai.OpenAiURI
import io.syspulse.skel.ext.{ExtClient, Detector, Contract, DetectorSchema, Trigger}
import io.syspulse.skel.ai.agent.AgentFunction
import io.syspulse.skel.ai.agent.Agent

class AgentExt(val uri:OpenAiURI,implicit val extClient:ExtClient) extends Agent with ExtCoreFunctions {

  import ExtJson._
  def getName(): String = "ext-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o_mini)
    //ModelId.gpt_3_5_turbo
  
  def getInstructions(): String = 
    """
    You are an Extractor Project and Contracts bot. Use the provided functions to answer questions.
    Always provide report about the actions you have taken with contract addresses and contract identifiers in the last message.
    Do not respond in one line message, always provide information about each attribute from function call response on a new line.
    """
  
  override def getTools(): Seq[AssistantTool] = ExtCoreFunctions.functions
  
  def getFunctions(): Map[String, AgentFunction] = coreFunctionsMap

}
