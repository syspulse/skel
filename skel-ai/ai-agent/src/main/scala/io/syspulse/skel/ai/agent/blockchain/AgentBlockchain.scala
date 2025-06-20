package io.syspulse.skel.ai.agent.blockchain

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
import play.api.libs.json.JsArray

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.openai.OpenAiURI
import io.syspulse.skel.ext.{ExtClient, Detector, Contract, DetectorSchema, Trigger}
import io.syspulse.skel.ai.agent.AgentFunction
import io.syspulse.skel.ai.agent.AgentAssistant
import requests._

object AgentBlockchainJson {
}

class AgentBlockchain(val uri:OpenAiURI) extends AgentAssistant {

  import AgentBlockchainJson._
  def getName(): String = "blockchain-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o_mini)
    //ModelId.gpt_3_5_turbo
  
  def getInstructions(): String = 
    """You are Blockchain Expert bot. Use the provided functions to retrieve information about blocks, transactions. 
    You are not an expert in hexadecimal format. Never try to convert or guess the block number or timestamp to decimal format by yourself.     
    Blocks can be referenced by block number in decimal or hexadecimal format. If decimal is used, convert it to hexadecimal using function tools.

    Response must contain the block number in human readable format, the block hash, the block timestamp in human readable format, and the block transactions count.
    """
  
  override def getTools(): Seq[AssistantTool] = Seq(
    FunctionTool(
      name = "getBlock",
      description = Some("Retrieve information about a block"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "blockNumber" -> Map(
            "type" -> "string",
            "description" -> "Block number in decimal or hexadecimal format. If decimal is used, convert it to hexadecimal with leading 0x."
          ),          
        ),
        "required" -> Seq("blockNumber"),        
      ),      
    ),
    
    FunctionTool(
      name = "blockNumberToHexadecimal",
      description = Some("Convert block number for getBlock function call to hexadecimal value"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "value" -> Map(
            "type" -> "string",
            "description" -> "Block number in decimal format"
          ),          
        ),
        "required" -> Seq("value"),
      ),      
    ),
    FunctionTool(
      name = "blockNumberToHumanReadable",
      description = Some("Convert block number from hexadecimal to human readable format"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "value" -> Map(
            "type" -> "string",
            "description" -> "Block number in any format"
          ),          
        ),
        "required" -> Seq("value"),
      ),      
    ),
    FunctionTool(
      name = "blockTimestampToHumanReadable",
      description = Some("Convert Block timestamp from getBlock response to human readable format"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "value" -> Map(
            "type" -> "string",
            "description" -> "Block timestamp in hexadecimal format"
          ),          
        ),
        "required" -> Seq("value"),
      ),      
    ),
  ) 
  
  def getFunctions(): Map[String, AgentFunction] = Map(
      "getBlock" -> new GetBlock,      
      "blockNumberToHexadecimal" -> new BlockNumberToHexadecimal,
      "blockNumberToHumanReadable" -> new BlockNumberToHumanReadable,
      "blockTimestampToHumanReadable" -> new BlockTimestampToHumanReadable,
    ) 
  
  class GetBlock extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val blockNumber = (functionArgsJson \ "blockNumber").as[String]
      
      val url = sys.env.get("ETH_RPC_URL").getOrElse("https://eth.llamarpc.com")
      // find contractId by address
      val body = s"""{"jsonrpc\":"2.0","method":"eth_getBlockByNumber","params":["${blockNumber}", false],"id":1}"""
      
      log.info(s"${blockNumber} -> ${url} (${body})")

      val rsp = requests.post(
        url = url,
        headers = Map("Content-Type" -> "application/json"),
        data = body
        )
      val json = rsp.text()      
      
      val blockObject = Json.parse(json)
      val transactionCount = (blockObject \ "result" \ "transactions").as[JsArray].value.size
            
      Json.obj(
        "block" -> blockObject, 
        "transaction_count" -> transactionCount
      )
    }
  }


  class BlockNumberToHexadecimal extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val value = (functionArgsJson \ "value").as[String]
      val result = Util.hex(BigInt(value).toByteArray)
      log.info(s"${value} -> ${result}")
      Json.obj(
        "result" -> result
      )
    }
  }

  class BlockNumberToHumanReadable extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val value = (functionArgsJson \ "value").as[String]
      val result = 
        if (value.startsWith("0x")) 
          BigInt(value.stripPrefix("0x"),16).toLong.toString
        else 
          value

      log.info(s"${value} -> ${result}")

      Json.obj(
        "result" -> result
      )
    }
  }

  class BlockTimestampToHumanReadable extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val value = (functionArgsJson \ "value").as[String]
      val result = Util.tsToString(BigInt(value.stripPrefix("0x"),16).toLong * 1000L)
      log.info(s"${value} -> ${result}")

      Json.obj(
        "result" -> result
      )
    }
  }
}