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
    You are a Contracts monitoring bot. Use the provided functions (addMonitoring, addContract) to answer questions if question asks to add, delete or set monitoring for contracts.
    If question was related to adding, deleting or setting monitoring for contracts, provide report about the actions you have taken with contract addresses and contract identifiers in the last message.
    Otherwise, just answer the question.
    """

  def getTools(): Seq[FunctionTool] = Seq(
    FunctionTool(
      name = "addMonitoring",
      description = Some("Adds new monitoring capabilities to the contract like Security Monitoring, Compliance Monitoring, Financial Monitoring, etc."),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "contractId" -> Map(
            "type" -> "string",
            "description" -> "Contract identifier returned by addContract function"
          ),
          "monitoring" -> Map(
            "type" -> "string",
            "enum" -> Seq("Security Monitoring", "Compliance Monitoring"),
            "description" -> "The type of monitoring to add to the contract"
          ),          
        ),
        "required" -> Seq("contractId","monitoring")
      )
    ),
    FunctionTool(
      name = "deleteContract",
      description = Some("Delete existing contrac by name or address)"),      
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "name" -> Map(
            "type" -> "string",
            "description" -> "Name of the Contract to be deleted"
          ),
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address of the contract to be deleted"
          ),
        ),        
      )
    ), 
    FunctionTool(
      name = "addContract",
      description = Some("Adds new contract to the project for monitoring and returns contract identifier (contractId)"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address of the contract in Ethereum format. For example: 0x742d35Cc6634C0532925a3b844f13573377189aF"
          ),
          "network" -> Map(
            "type" -> "string",
            "enum" -> Seq("Ethereum", "Arbitrum", "Optimism", "Base", "Polygon", "BNB Chain", "Solana", "Bitcoin", "Other"),
            "description" -> "The network where the contract is deployed on"
          ),
          "name" -> Map(
            "type" -> "string",
            "description" -> "Name of the contract. If not provided, the address will be used as the name."
          ),
        ),
        "required" -> Seq("address","network","name")
      )
    ),
  )
  
  def getFunctions(): Map[String, AiFunction] = Map(
      "addMonitoring" -> new AddMonitoring,
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
        "contractId" -> contractId,
        //"action" -> s"Contract added: ${address} on ${network} with name ${name}"
      )
    }
  }

  class AddMonitoring extends AiFunction {
    def run(functionArgsJson: JsValue): JsValue = {
      val contractId = (functionArgsJson \ "contractId").as[String]
      val monitoring = (functionArgsJson \ "monitoring").as[String]
      Json.obj(
        "monitoring" -> monitoring, 
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
