package io.syspulse.skel.ai.agent

import com.typesafe.scalalogging.Logger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try,Success,Failure}

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer

import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.wsclient.service.CloseableService
import io.cequence.openaiscala.domain.response.Assistant
import io.cequence.openaiscala.domain.response.DeleteResponse

import io.cequence.openaiscala.domain.responsesapi.Response
import io.cequence.openaiscala.domain.responsesapi.Inputs
import io.cequence.openaiscala.domain.responsesapi.CreateModelResponseSettings
import io.cequence.openaiscala.domain.responsesapi.tools.ToolChoice
import io.cequence.openaiscala.domain.responsesapi.tools.FunctionTool

import play.api.libs.json.Json
import play.api.libs.json.JsValue

import io.cequence.wsclient.service.PollingHelper
import io.cequence.openaiscala.domain.ThreadFullMessage
import io.cequence.openaiscala.domain.ModelId

import io.syspulse.skel.ai.core.openai.OpenAiURI
import io.cequence.openaiscala.domain.ThreadMessageContent
import io.cequence.openaiscala.domain.ChatRole
import io.cequence.openaiscala.domain.ThreadMessageContentType
import io.cequence.openaiscala.domain.ThreadMessageText

trait AgentModelResponse extends Agent {
  
  @volatile
  var responses:Option[Response] = None

  def getId(): Option[String] = {
    if(uri.aid.isDefined) 
      uri.aid
    else
      responses.map(_.id)
  }

  protected def getTools(): Seq[FunctionTool]

  def delete(): Future[DeleteResponse] = {
    deleteResponses()
  }

  protected def deleteResponses(): Future[DeleteResponse] = {
    responses match {
      case Some(r) =>
        log.info(s"delete response_id: '${r.id}'")        
        service
          .deleteModelResponse(r.id)
          .map(r => {
            log.info(s"delete response_id: ${r.id}: ${r}")
            // TODO: fix this
            io.cequence.openaiscala.domain.response.DeleteResponse.Deleted
          })
      case None =>
        Future.failed(new IllegalStateException(s"No reponse_id: ${uri.aid}"))
      }
  }
  
  def ask(question:String, instructions:Option[String] = None, metadata:Option[Map[String,String]] = None): Future[Try[Seq[ThreadFullMessage]]] = {    
    service
      .createModelResponse(
        Inputs.Text(question),
        settings = CreateModelResponseSettings(
          model = getModel(),
          tools = getTools(),
          toolChoice = Some(ToolChoice.Mode.Auto),
          stream = Some(true)
        )
      )
      .map { response =>
        log.info(s"response: ${response}")
        
        val functionCall = response.outputFunctionCalls.headOption.map(functionCall => {
          println(
            s"""Function Call Details:
              |Name: ${functionCall.name}
              |Arguments: ${functionCall.arguments}
              |Call ID: ${functionCall.callId}
              |ID: ${functionCall.id}
              |Status: ${functionCall.status}""".stripMargin
          )
          functionCall
        })
        

        val toolsUsed = response.tools.map(_.typeString)

        println(s"${toolsUsed.size} tools used: ${toolsUsed.mkString(", ")}")

        Success(Seq(ThreadFullMessage(
          id = "1",
          created_at = new java.util.Date(),
          thread_id = "1",
          role = ChatRole.Assistant,
          content = Seq(ThreadMessageContent(
            `type` = ThreadMessageContentType.text,
            text = Some(ThreadMessageText(
              value = response.output.map(o => o.toString).mkString("\n"),
              annotations = Seq()
            ))
          ))
        )))
      }
  }
  
}
