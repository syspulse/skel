package io.syspulse.skel.ai.agent

import com.typesafe.scalalogging.Logger

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
import io.cequence.wsclient.service.CloseableService
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.JsValue

import scala.util.Try
import scala.util.Success
import scala.util.Failure
import io.cequence.openaiscala.domain.AssistantTool.FileSearchTool

trait AgentFile extends Agent {

  def getVectorStoreId(): String

  override protected def createAssistant(instructions: String) =
    for {
      assistant <- service.createAssistant(
        model = getModel(),
        name = Some(getName()),
        instructions = Some(instructions),          
        tools = Seq(FileSearchTool()),
        toolResources = Some(
          AssistantToolResource(
            AssistantToolResource.FileSearchResources(
              vectorStoreIds = Seq(getVectorStoreId())
            )
          )
        )
      )
    } yield assistant

  def getTools(): Seq[FunctionTool] = Seq()

}
