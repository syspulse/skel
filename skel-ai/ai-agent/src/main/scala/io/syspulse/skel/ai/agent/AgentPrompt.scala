package io.syspulse.skel.ai.agent

import scala.util.{Try,Success,Failure}

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
import io.syspulse.skel.ai.core.openai.OpenAiURI
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

class AgentPrompt(val uri:OpenAiURI) extends AgentFile {
    
  def getName(): String = "prompt-agent"

  override def getModel() = uri.model.getOrElse(ModelId.gpt_3_5_turbo)    
  
  def getInstructions(): String = ""
  def getVectorStoreId(): String = ""  
  def getFunctions(): Map[String, AgentFunction] = Map()

  override def ask(question:String, instructions:Option[String] = None, metadata:Option[Map[String,String]] = None): Future[Try[Seq[ThreadFullMessage]]] = {
    val ff = for {
      completion <- service.createChatCompletion(
        messages = Seq(
          UserMessage(question),
      ),
      settings = CreateChatCompletionSettings(
        model = getModel(),
        // temperature = Some(0.5),
        // top_p = Some(0.5),
        // n = Some(1),
        // stop = Seq("###")
        )
      )
    } yield completion

    ff
    .map{ 
      case rsp:ChatCompletionResponse => 
        Success(
          rsp.choices.map(c => {
            ThreadFullMessage(
              id = rsp.id,
              created_at = rsp.created,
              thread_id = "",
              role = c.message.role,
              content = Seq(ThreadMessageContent(
                text = Some(ThreadMessageText(c.message.content,Seq())),
                `type` = ThreadMessageContentType.text
              ))
            )
          })
        )
      case _ => Failure(new IllegalStateException("Failed to get final messages"))
    }
    .recover {    
      case e: Throwable =>
        log.error(s"failed to run chat",e)
        Failure(e)
    }
  }
}
