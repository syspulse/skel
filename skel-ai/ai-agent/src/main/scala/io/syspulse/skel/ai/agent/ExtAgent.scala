package io.syspulse.skel.ai.agent

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

class ExtAgent(model:Option[String] = None) extends Agent {
  
  def getName(): String = "ext-agent"

  override def getModel() = 
    model.getOrElse(ModelId.gpt_4o)
    //ModelId.gpt_3_5_turbo
  
  def getInstructions(): String = 
    """
    You are an Extractor Project and Contracts bot. Use the provided functions to answer questions.
    Always provide report about the actions you have taken with contract addresses and contract identifiers in the last message
    """

  def getTools(): Seq[FunctionTool] = Seq(
    FunctionTool(
      name = "addMonitoringType",
      description = Some("Add new monitoring capabilities to the contract like Security Monitoring, Compliance Monitoring, Financial Monitoring, etc. by Address."),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "address" -> Map(
            "type" -> "string",
            "description" -> "Contract Address. Must be provided by user."
          ),
          "monitoringType" -> Map(
            "type" -> "string",
            "enum" -> Seq("Security Monitoring", "Compliance Monitoring"),
            "description" -> "The type of monitoring to add to the contract. Infer type from the question."
          ),          
        ),
        "required" -> Seq("address","monitoringType"),
        // "additionalProperties" -> false
      ),
      // strict = Some(true)
    ),
    FunctionTool(
      name = "deleteContract",
      description = Some("Delete existing contrac by name or address"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "name" -> Map(
            "type" -> "string",
            "description" -> "Name of the Contract to be deleted. User must provide Contract Name."
          ),
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address of the contract to be deleted. User must provide valid address in question."
          ),
        ),
        "required" -> Seq("name","address"),
        // "additionalProperties" -> false
      ),
      // strict = Some(true)
    ), 
    FunctionTool(
      name = "addContract",
      description = Some("Add new contract to the project for monitoring and return contract identifier (contractId)"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address of the contract in Ethereum format. User must provide valid address."
          ),
          "network" -> Map(
            "type" -> "string",
            "enum" -> Seq("Ethereum", "Arbitrum", "Optimism", "Base", "Polygon", "BNB Chain", "Solana", "Bitcoin", "Other"),
            "description" -> "The network where the contract is deployed on. User must provide valid network."
          ),
          "name" -> Map(
            "type" -> "string",
            "description" -> "Name of the contract. Infer from the question."
          ),
        ),
        "required" -> Seq("address","network","name"),
        // "additionalProperties" -> false
      ),
      // strict = Some(true)
    ),
  )
  
  def getFunctions(): Map[String, AiFunction] = Map(
      "addMonitoringType" -> new AddMonitoringType,
      "addContract" -> new AddContract,
      "deleteContract" -> new DeleteContract
    )
    

  // unit is ignored here
  class AddContract extends AiFunction {
    def run(functionArgsJson: JsValue): JsValue = {
      val address = (functionArgsJson \ "address").as[String]
      val network = (functionArgsJson \ "network").as[String]
      val name = (functionArgsJson \ "name").asOpt[String]           
      val contractId = scala.util.Random.nextInt(1000)
      Json.obj(
        "address" -> address, 
        "network" -> network, 
        "name" -> name, 
        // "contractId" -> contractId,
        "error" -> s"Failed to add contract: Error 500 from API"
      )
    }
  }

  class AddMonitoringType extends AiFunction {
    def run(functionArgsJson: JsValue): JsValue = {
      val contractAddress = (functionArgsJson \ "address").as[String]
      val monitoringType = (functionArgsJson \ "monitoringType").as[String]
      
      // find contractId by address
      val contractId = contractAddress.hashCode

      Json.obj(
        "monitoringType" -> monitoringType, 
        "contractId" -> contractId,
        //"action" -> s"Monitoring type ${monitoring} for contract ${contractId}"
      )
    }
  }

  class DeleteContract extends AiFunction {
    def run(functionArgsJson: JsValue): JsValue = {
      val address = (functionArgsJson \ "address").asOpt[String]
      val name = (functionArgsJson \ "name").asOpt[String]

      log.info(s"DELETE: ${address}/${name}")
      Json.obj("address" -> address, "name" -> name)
    }
  }
}
