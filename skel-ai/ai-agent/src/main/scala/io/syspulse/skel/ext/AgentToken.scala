package io.syspulse.skel.ext

import scala.util.{Try,Success,Failure}

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

import io.syspulse.blockchain.Token

object AgentTokenJson {
  implicit val tokenWrites: Writes[Token] = Json.writes[Token]
}

class AgentToken(val uri:OpenAiURI,implicit val extClient:ExtClient) extends Agent {

  import AgentTokenJson._
  def getName(): String = "token-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o_mini)
    //ModelId.gpt_3_5_turbo
  
  def getInstructions(): String = 
    """You are an ERC-20 Tokens export bot. You know everything about ERC-20 Tokens, their functionality.    
    Tokens can be referenced by name or symbol (ticker) usually uppercase.
    Use the provided functions to retrieve information about ERC-20 Tokens.
    If user asks about all tokens (for example number of all tokens or list of all tokens), than this is a heavy and expensive operation, 
    thus call it only once and work with returned results. Do not call getAllTokens multiple times in the same question.
    Always provide report about the actions you have taken with contract addresses and parameters executed.    
    """
  
  override def getTools(): Seq[AssistantTool] = Seq(
    FunctionTool(
      name = "getToken",
      description = Some("Retrieve information about token"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "tokenAddress" -> Map(
            "type" -> "string",
            "description" -> "Address of the ERC-20Token contract in Ethereum format. Optional, if not provided then tokenName is used"
          ),
          "network" -> Map(
            "type" -> "string",
            "enum" -> ExtCoreFunctions.networkTypes,
            "description" -> "The network where the token contract is deployed. Infer from the question and leave empty if not clear."
          ),
          "tokenName" -> Map(
            "type" -> "string",
            "description" -> "Name of the token. Infer name from the question. Optional, if not provided then tokenAddress is used"
          ),          
        ),
        "required" -> Seq(),        
      ),      
    ),
    FunctionTool(
      name = "getAllTokens",
      description = Some("Retrieve information about all tokens known to the agent"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "network" -> Map(
            "type" -> "string",
            "enum" -> ExtCoreFunctions.networkTypes,
            "description" -> "The network where the token contract is deployed. Infer from the question and leave empty if not clear."
          ),
        ),
        "required" -> Seq(),
      ),      
    ),
  ) 
  
  def getFunctions(): Map[String, AgentFunction] = Map(
      "getToken" -> new GetToken,
      "getAllTokens" -> new GetAllTokens,      
    ) //++ coreFunctionsMap
  
  class GetToken extends AgentFunction {
    
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val tokenAddress = (functionArgsJson \ "tokenAddress").asOpt[String]
      val tokenName = (functionArgsJson \ "tokenName").asOpt[String]
      val network = (functionArgsJson \ "network").asOpt[String]
      
      val tt = askToken(tokenName,tokenAddress)

      Json.toJson(tt)
    }
  }

  class GetAllTokens extends AgentFunction {    

    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val network = (functionArgsJson \ "network").asOpt[String]
      
      val tt = askToken(None,None)

      val ttFiltered = if(network.isDefined) 
        tt.filter(_.bid.equalsIgnoreCase(network.get))
      else 
        tt

      val ttResult = ttFiltered
      //val ttJson = Json.toJson(ttResult)
      val ttJson = ttResult.map(t => Json.obj(
        "symbol" -> t.sym
      ))

      Json.obj(
        "total" -> ttResult.size, 
        "tokens" -> ttJson       
      )      
    }
  }

  def askToken(token:Option[String],address:Option[String]):Set[Token] = {
    if(address.isDefined) 
      Token.resolve(address.get)
    else if(token.isDefined) 
      Token.resolve(token.get)
    else 
      // get all tokens
      Token.tokensSym.values.flatten.toSet
      //throw new IllegalArgumentException("Missing token name or address")
  }  
}
