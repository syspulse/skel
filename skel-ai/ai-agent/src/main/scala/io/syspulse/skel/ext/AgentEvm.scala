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

object EvmJson {

}

class AgentEvm(val uri:OpenAiURI,extClient:ExtClient) extends Agent {

  import EvmJson._
  def getName(): String = "evm-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o)
    //ModelId.gpt_3_5_turbo
  
  def getInstructions(): String = 
    """
    You are an EVM Contracts function calls bot. Use the provided functions to execute function calls on EVM contracts and return results.
    Derive correct parameter from the question.
    Always provide report about the actions you have taken with contract addresses and parameters executed.
    """

  val networkTypes = Seq("", "Ethereum", "Arbitrum", "Optimism", "Base", "Polygon", "BSC", "Solana", "Bitcoin")  
  
  override def getTools(): Seq[AssistantTool] = Seq(
    FunctionTool(
      name = "callContract",
      description = Some("Execute contract function and return result"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address of the contract in Ethereum format. Optional, if not provided then contractName is used"
          ),
          "network" -> Map(
            "type" -> "string",
            "enum" -> networkTypes,
            "description" -> "The network where the contract is deployed. Optional."
          ),
          "contractName" -> Map(
            "type" -> "string",
            "description" -> "Name of the contract. Infer name from the question. Optional, if not provided then address is used"
          ),
          "functionName" -> Map(
            "type" -> "string",
            "description" -> "Name of the contact function to call"
          ),
          "functionParams" -> Map(
            "type" -> "array",
            "description" -> "Parameters of the function to call. Function may have none or multiple parameters.",
            "items" -> Map(
              "type" -> "string",
              "description" -> "Parameter of the function"
            )
          ),
        ),
        "required" -> Seq(),
        // "additionalProperties" -> false
      ),
      // strict = Some(true)
    ),
  )
  
  def getFunctions(): Map[String, AgentFunction] = Map(
      "callContract" -> new CallContract,      
    )
  
  class CallContract extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val address = (functionArgsJson \ "address").asOpt[String]
      val contractName = (functionArgsJson \ "contractName").asOpt[String]
      val network = (functionArgsJson \ "network").asOpt[String]
      val functionName = (functionArgsJson \ "functionName").as[String]
      val functionParams = (functionArgsJson \ "functionParams").asOpt[Seq[String]]
      
      // find contractId by address
      val contracts = extClient.getProjectContracts(projectId, address)
        .filter(c => address.isEmpty || c.address.toLowerCase == address.get.toLowerCase)
        .filter(c => network.isEmpty || c.network.toLowerCase == network.get.toLowerCase)
        .filter(c => contractName.isEmpty || c.name.toLowerCase == contractName.get.toLowerCase)
      
      val results = contracts        
        .map(c => {
          val contractId = c.contractId
          val addr = c.address

          // get abi
          val contractWithAbi = extClient.getContract(projectId, contractId.toInt)
          val result = contractWithAbi.abi match {
            case Some(abi) => 
              val functionCall = abi.getFunctionCall(functionName)
              val params = functionParams.getOrElse(Seq())
              
              log.info(s"${address}/${contractName}: ${functionName} ==> Contract(${contractId},${c.name},${c.address}) --> '${functionCall} ${params}'")
              
              if(functionCall.isSuccess) {
                // try to call 
                try {
                  extClient.callContract(c.address, c.network, functionCall.get, params)
                } catch {
                  case e:Exception => 
                    log.error(s"failed to call contract: ${c.address}: ${functionCall.get}: ${e.getMessage}")
                    s"${e.getMessage}"
                }
                
              } else {
                val err = s"${functionCall.failed.get.getMessage}"
                log.error(s"failed to call contract: ${c.address}: ${err}")
                err
              }
            case None => 
              val err = "No ABI"
              log.error(s"failed to call contract: ${c.address}: ${err}")
              err
          }
          
          (result,contractWithAbi)
        })      

      Json.obj(
        "functionName" -> functionName, 
        "functionParams" -> functionParams,
        "contracts" -> Json.arr(
          results.map(r => {
            val c = r._2
            Json.obj(
              "address" -> c.address,
              "network" -> c.network,
              "name" -> c.name,
              "result" -> r._1,
            )
          })
        )
      )
    }
  }
  
}
