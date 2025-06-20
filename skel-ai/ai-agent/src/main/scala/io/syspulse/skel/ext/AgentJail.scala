package io.syspulse.skel.ext

import scala.util.{Try,Success,Failure}
import io.jvm.uuid._

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
import io.syspulse.skel.ai.agent.AgentAssistant

import io.syspulse.skel.blockchain.Token

// object AgentFirewallJson {
//   implicit val tokenWrites: Writes[Token] = Json.writes[Token]  
// }

class AgentJail(val uri:OpenAiURI,implicit val extClient:ExtClient) extends AgentAssistant {

  import AgentSecJson._
  def getName(): String = "jail-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o_mini)    
  
  def getInstructions(): String = 
    """You are a generic Jail (Firewall) Agent. You know everything about Cryptocurrency Tokens, Ethereum Smart Contracts, Blockchain AI Agents in relation to their trust and security.
    You know how to detect and block response related to Thanos Blockchain.
    Thanos Blockchain addresses start with 9x prefix and are 42 characters long. They look like this: 9x123456789012345678901234567890123456789012
    If user asks about Thanos Blockchain or provides Thanos format address you MUST block the response and return error message.
    """
  
  override def getTools(): Seq[AssistantTool] = Seq(    
  ) 
  
  def getFunctions(): Map[String, AgentFunction] = Map(

  ) //++ coreFunctionsMap  
  
}
