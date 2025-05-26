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

import io.syspulse.skel.blockchain.Token

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
    """You are an ERC-20 Tokens expert bot. You know everything about ERC-20 Tokens, their functionality.    
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
            "description" -> "Address of the ERC-20 Token contract in Ethereum format. Optional, if not provided then tokenName is used"
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
    FunctionTool(
      name = "getTokenPrice",
      description = Some("Retrieve price for token"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "tokenAddress" -> Map(
            "type" -> "string",
            "description" -> "Address of the ERC-20 Token contract in Ethereum format. Optional, if not provided then tokenName is used"
          ),
          "tokenName" -> Map(
            "type" -> "string",
            "description" -> "Name of the token. Infer name from the question. Optional, if not provided then tokenAddress is used"
          ),
        ),
        "required" -> Seq(),        
      ),      
    ),
  ) 
  
  def getFunctions(): Map[String, AgentFunction] = Map(
      "getToken" -> new GetToken,
      "getAllTokens" -> new GetAllTokens,
      "getTokenPrice" -> new GetTokenPrice,
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
      Token.default.all().map(t => Token.coinToToken(t)).flatten.toSet
      //throw new IllegalArgumentException("Missing token name or address")
  }

  def getPriceCoingecko(addr:String,blockchain:String = "ethereum",apiKey:String = ""):Try[Double] = {
    val url = s"https://pro-api.coingecko.com/api/v3/coins/${blockchain}/contract/${addr}"
    try {
      log.info(s"--> ${url}")

      val rsp = requests.get(
        url = url,
        headers = Seq( ("Content-Type" -> "application/json"), ("x-cg-pro-api-key" -> apiKey) )
      )

      log.debug(s"rsp = ${rsp}")

      val r = rsp.statusCode match {
        case 200 => 
          val json = ujson.read(rsp.text())
          val ticker = json.obj("symbol").str
          val price = json.obj("market_data").obj("current_price").obj("usd").num
          Success(price)
        case _ => 
          Failure(new Exception(s"failed request: ${rsp}"))
      }
      
      r
    } catch {
      case e:Exception => 
        Failure(new Exception(s"failed request -> '${url}'",e))
    }
  }

  class GetTokenPrice extends AgentFunction {    
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val tokenAddress = (functionArgsJson \ "tokenAddress").asOpt[String]
      val tokenName = (functionArgsJson \ "tokenName").asOpt[String]      
      
      val tt = askToken(tokenName,tokenAddress)

      if(tt.size == 0) {
        return Json.obj(
          "error" -> "No token found"
        )
      }

      // find address for blockchain
      val (blockchain,addr) = tt.filter(_.bid.equalsIgnoreCase("ethereum")).toList match {
        case t :: Nil => (t.bid,t.addr)
        case Nil => 
          return Json.obj(
            "error" -> "No token found"
          )
        case t :: _ => 
          // take first if ethereum is not found
          (t.bid,t.addr)
      }

      getPriceCoingecko(addr,blockchain = blockchain,apiKey = sys.env.getOrElse("CG_API_KEY","")) match {
        case Success(price) => 
          Json.obj(
            "token" -> addr, 
            "price" -> price
          )
        case Failure(e) => 
          Json.obj(
            "error" -> e.getMessage
          )
      }
    }
  }
}
