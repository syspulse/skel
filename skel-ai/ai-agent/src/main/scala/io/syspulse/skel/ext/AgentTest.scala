package io.syspulse.skel.ext


import io.cequence.openaiscala.domain.responsesapi.tools.FunctionTool

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
import io.syspulse.skel.ai.agent.AgentFunction
import io.syspulse.skel.ai.agent.AgentModelResponse
import io.cequence.openaiscala.domain.JsonSchema

class AgentTest(val uri:OpenAiURI) extends AgentModelResponse {

  def getName(): String = "test-agent"

  override def getModel() = uri.model.getOrElse(ModelId.gpt_4o_mini)    
  
  def getInstructions(): String = 
    """You are an agent who know prices of tokens. Use the provided functions to retrieve data from the system.
    Always provide report about the actions you have taken functions and parameters executed.
    """
  
  override def getTools(): Seq[FunctionTool] = Seq(
    FunctionTool(
      name = "get_current_weather",
      parameters = JsonSchema.Object(
        properties = Map(
          "location" -> JsonSchema.String(
            description = Some("The city and state, e.g. San Francisco, CA")
          ),
          "unit" -> JsonSchema.String(
            `enum` = Seq("celsius", "fahrenheit")
          )
        ),
        required = Seq("location", "unit"),
        // additionalProperties = false
      ),
      description = Some("Get the current weather in a given location"),
      strict = false
    )
  )
  
  def getFunctions(): Map[String, AgentFunction] = Map(
      "get_current_weather" -> new GetPrice,      
    ) 
  
  class GetPrice extends AgentFunction {
    def run(functionArgsJson: JsValue, metadata:Map[String,String]): JsValue = {
      val location = (functionArgsJson \ "location").asOpt[String]
      val unit = (functionArgsJson \ "unit").asOpt[String]
      
      // find contractId by address
      Json.obj(
        "location" -> location, 
        "unit" -> unit,
        "temperature" -> "130.65",        
      )
    }
  }
  
}
