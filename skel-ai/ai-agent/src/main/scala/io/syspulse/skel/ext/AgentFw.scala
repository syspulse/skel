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
import io.syspulse.skel.ai.agent.Agent

import io.syspulse.skel.blockchain.Token

// object AgentFirewallJson {
//   implicit val tokenWrites: Writes[Token] = Json.writes[Token]  
// }

case class AgentFwConfig(
  instructions:String
)

class AgentFw(val uri:OpenAiURI,val conf:AgentFwConfig,implicit val extClient:ExtClient) extends Agent {

  import AgentSecJson._
  def getName(): String = "fw-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o_mini)    
  
  def getInstructions(): String = conf.instructions

  override def getTools(): Seq[AssistantTool] = Seq(    
  ) 
  
  def getFunctions(): Map[String, AgentFunction] = Map(

  ) //++ coreFunctionsMap  
  
}
