package io.syspulse.skel.ai.agent

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

object ExtJson {
  implicit val detectorWrites = new Writes[Detector] {
    def writes(d: Detector) = Json.obj(
      "name" -> d.name,
      "id" -> d.detectorId,
      "did" -> d.did
    )
  }

  implicit val detectorSchemaWrites = new Writes[DetectorSchema] {
    def writes(d: DetectorSchema) = Json.obj(
      "id" -> d.schemaId,
      "did" -> d.did
    )
  }

  implicit val contractWrites = new Writes[Contract] {
    def writes(c: Contract) = Json.obj(
      "address" -> c.address,
      "network" -> c.network,
      "name" -> c.name,
      "contractId" -> c.contractId
    )
  }

  implicit val contractsWrites: Writes[Seq[Contract]] = Writes.seq[Contract]
  implicit val detectorsWrites: Writes[Seq[Detector]] = Writes.seq[Detector]
  implicit val detectorSchemasWrites: Writes[Seq[DetectorSchema]] = Writes.seq[DetectorSchema]
}

class ExtAgent(val uri:OpenAiURI,extClient:ExtClient) extends Agent {

  import ExtJson._
  def getName(): String = "ext-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o)
    //ModelId.gpt_3_5_turbo
  
  def getInstructions(): String = 
    """
    You are an Extractor Project and Contracts bot. Use the provided functions to answer questions.
    Always provide report about the actions you have taken with contract addresses and contract identifiers in the last message
    """

  override def getTools(): Seq[AssistantTool] = Seq(
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
    FunctionTool(
      name = "getProjectContracts",
      description = Some("Get all contracts configured in the current Project or user provided Project name"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(          
          "name" -> Map(
            "type" -> "string",
            "description" -> "Name of the Project. If not provided, current Project will be used."
          ),
        ),        
      ),      
    ),
  )
  
  def getFunctions(): Map[String, AgentFunction] = Map(
      "addMonitoringType" -> new AddMonitoringType,
      "addContract" -> new AddContract,
      "deleteContract" -> new DeleteContract,
      "getProjectContracts" -> new GetProjectContracts
    )

  // unit is ignored here
  class AddContract extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val address = (functionArgsJson \ "address").as[String]
      val network = (functionArgsJson \ "network").as[String]
      val name = (functionArgsJson \ "name").asOpt[String]           
      val contractId = scala.util.Random.nextInt(1000)

      log.info(s"${address}/${network}/${name} [+] Project($projectId)")

      val contract = extClient.addContract(projectId, address, network, name.getOrElse(address))

      // Json.obj(
      //   "address" -> address, 
      //   "network" -> network, 
      //   "name" -> name, 
      //   "contractId" -> contract.contractId        
      // )
      Json.toJson(contract)
    }
  }

  class AddMonitoringType extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val contractAddress = (functionArgsJson \ "address").as[String]
      val monitoringType = (functionArgsJson \ "monitoringType").as[String]
      
      // find contractId by address
      val contracts = extClient.getProjectContracts(projectId, Some(contractAddress))
      val contractIds = contracts.map(_.contractId)

      log.info(s"${contractAddress}/${monitoringType} [+] Project($projectId)[${contractIds}]")

      // add AML detector
      val detectors = for(contractId <- contractIds) yield {
        val d1 = extClient.addDetector(
          pid = projectId,
          cid = contractId,
          did = "DetectorAML",
          name = "AML Monitor",  
          tags = "COMPLIANCE",
          sev = -1,
          conf = ujson.Obj()
        )

        log.info(s"${contractId}/${contractAddress}: [+] Detector(${d1.detectorId})")

        // add TVL 
        val d2 = extClient.addDetector(
          pid = projectId,
          cid = contractId,
          did = "TVL Monitor",
          name = "TVL Monitor",  
          tags = "COMPLIANCE",
          sev = -1,
          conf = ujson.Obj(
            "tokens" -> ujson.Arr()
          )
        )        
        log.info(s"${contractId}/${contractAddress}: [+] Detector(${d2.detectorId})")

        (contractId,Seq(d1,d2))
      }

      Json.obj(
        "monitoringType" -> monitoringType, 
        "contract" -> Json.arr(
          contracts.map(c => Json.obj(
            "address" -> c.address,
            "network" -> c.network,
            "name" -> c.name,
            "contractId" -> c.contractId,
            "detectors" -> detectors.filter(_._1 == c.contractId).map(d => {
              Json.arr(d._2.map(d => Json.obj(
                "id" -> d.detectorId,
                "name" -> d.name,
                "did" -> d.did
              )))
            })
          ))
        )
      )
    }
  }

  class DeleteContract extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val address = (functionArgsJson \ "address").asOpt[String]
      val name = (functionArgsJson \ "name").asOpt[String]
      
      log.info(s"${address}/${name} [-] Project($projectId)")

      val contract = extClient.delContract(projectId, address, name)

      // Json.obj("address" -> contract.address, "status" -> "deleted")
      Json.toJson(contract)
    }
  }

  class GetProjectContracts extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {      
      val projectId = metadata.getOrElse("pid","???")

      log.info(s"[?] Project($projectId)")

      val contracts = extClient.getProjectContracts(projectId)

      // Json.arr(contracts.map { contract =>
      //   Json.obj(
      //     "address" -> contract.address, 
      //     "network" -> contract.network, 
      //     "name" -> contract.name, 
      //   )
      // })
      Json.toJson(contracts)
    }
  }
}
