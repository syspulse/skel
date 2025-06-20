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

case class SecurityScore(
  address:String,
  name:String,  

  tags:Seq[String],
  score:Option[Double],

  status:String
)

case class SecurityScoreJob(
  jobId:String,
  address:String,  
  status:String
)

object AgentSecJson {
  implicit val tokenWrites: Writes[Token] = Json.writes[Token]
  implicit val scoreWrites: Writes[SecurityScore] = Json.writes[SecurityScore]
  implicit val jobWrites: Writes[SecurityScoreJob] = Json.writes[SecurityScoreJob]
}

class AgentSec(val uri:OpenAiURI,implicit val extClient:ExtClient) extends AgentAssistant {

  import AgentSecJson._
  def getName(): String = "sec-agent"

  override def getModel() = 
    uri.model.getOrElse(ModelId.gpt_4o_mini)    
  
  def getInstructions(): String = 
    """You are Crypto Security expert bot. You know everything about Cryptocurrency Tokens, Ethereum Smart Contracts, Blockchain AI Agents in relation to their trust and security.
    You know to calculate and retrieve security or trust score for Address, Contract, Token, Agent.
    Tokens can be referenced by name or symbol (ticker) usually uppercase.
    Agents can be referenced by name or address.
    Use the provided functions to retrieve existing security score (trust score). If you do not know the answer, start a calculation process and return job_id to the user.
    Always provide report about the actions you have taken with blockchain addresses, status, score and tags.
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
      name = "getSecurityScore",
      description = Some("Retrieve existing security score or return instructions to start a job to calculate security score. Score parameter in response contains number between 0.0 and 100.0. The higher, the better. Score below 50.0 means address is suspecious. Score below 25.0 mean adderess is dangerous. Score above 75.0 usually means address is safe and trusted."),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address for which to get security score"
          ),
          "name" -> Map(
            "type" -> "string",
            "description" -> "Name for which to get security score. Optional, if not provided then Address is used"
          ),
        ),
        "required" -> Seq(),
      ),      
    ),
    FunctionTool(
      name = "startJob",
      description = Some("Start the job to calculate security score. Return jobId to the user. User should use getSecurityScore to retrieve the result when job is finished."),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "address" -> Map(
            "type" -> "string",
            "description" -> "Address for which to calculate security score"
          ),          
        ),
        "required" -> Seq("address"),
      ),      
    ),
    FunctionTool(
      name = "getJob",
      description = Some("Returns the status for secruity score calculation job."),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "jobId" -> Map(
            "type" -> "string",
            "description" -> "jobId returned by startJob"
          ),          
        ),
        "required" -> Seq("jobId"),
      ),      
    ),
  ) 
  
  def getFunctions(): Map[String, AgentFunction] = Map(
      "getToken" -> new GetToken,
      "getAllTokens" -> new GetAllTokens,
      "getSecurityScore" -> new GetSecurityScore,
      "startJob" -> new StartJob,
      "getJob" -> new GetJob,
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

  def askSecurityScore(addr:String,name:String):Try[SecurityScore] = {    
    try {
      log.info(s"-----------------------> AML(${addr})")
      val amlData = extClient.getAml(addr)
        Success(
          SecurityScore(
            address = addr,
            name = name,
            score = amlData.score,
            tags = amlData.tags,
            status = "finished"
          )
        )
    } catch {
      case e:Exception => 
        log.error(s"failed to get security score: ${addr}:", e)
        Failure(e)
    }
  }

  class GetSecurityScore extends AgentFunction {    
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val address = (functionArgsJson \ "address").asOpt[String]
      val name = (functionArgsJson \ "name").asOpt[String]      
      
      val tt = askToken(name,address)
      log.info(s"tokens: ${tt}")

      val addr = if(tt.size > 0) {
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
        addr
      } else {
        address.getOrElse(throw new IllegalArgumentException("Missing address"))
      }
      
      askSecurityScore(addr,name.getOrElse(addr)) match {
        case Success(score) => 
          Json.toJson(score)
        case Failure(e) => 
          Json.obj(
            "error" -> e.getMessage
          )
      }
    }
  }

  var jobs = Map[String,SecurityScoreJob]()

  class StartJob extends AgentFunction {    
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val address = (functionArgsJson \ "address").as[String]      

      val job = SecurityScoreJob(
        jobId = UUID.randomUUID().toString,
        address = address,
        status = "started"
      )
      
      val r = Json.toJson(job)
      
      jobs = jobs + (job.jobId -> job.copy(status = "running"))

      r
    }
  }

  class GetJob extends AgentFunction {    
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val projectId = metadata.getOrElse("pid","???")
      val jobId = (functionArgsJson \ "jobId").as[String]      

      jobs.get(jobId) match {
        case Some(job) => 
          Json.toJson(job)
        case None => 
          Json.obj(
            "error" -> s"Job not found: ${jobId}"
          )
      }

    }
  }
}
