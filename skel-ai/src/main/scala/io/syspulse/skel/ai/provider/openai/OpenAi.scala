package io.syspulse.skel.ai.provider.openai

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.ai.Ai
import io.syspulse.skel.ai.core.Providers
import io.syspulse.skel.ai.core.openai.OpenAiURI
import io.syspulse.skel.ai.Prompt
import io.syspulse.skel.ai.PromptMessage
import io.syspulse.skel.ai.provider.AiProvider

case class OpenAi_ChatMessage(
  role: String,
  content: String,   
)

case class OpenAi_ChatChoices(
  index: Int,
  message: OpenAi_ChatMessage,
  finish_reason: String
)

case class OpenAi_ChatUsage(
  prompt_tokens: Int,
  completion_tokens: Int,
  total_tokens: Int,
)

case class OpenAi_ChatRes(
  id:String,
  `object`:String,
  created: Long,
  model:String,
  
  choices: Seq[OpenAi_ChatChoices],

  usage: OpenAi_ChatUsage,
  system_fingerprint:Option[String]
)

case class OpenAi_ChatReqMsg(
  role:String,
  content:String
)

case class OpenAi_ChatReq(
  model:String,
  messages:Seq[OpenAi_ChatReqMsg]
)

object OpenAi_Json extends JsonCommon {  
  implicit val jf_oai_msg = jsonFormat2(OpenAi_ChatMessage)
  implicit val jf_oai_cho = jsonFormat3(OpenAi_ChatChoices)
  implicit val jf_oai_usg = jsonFormat3(OpenAi_ChatUsage)
  implicit val jf_oai_chat_res = jsonFormat7(OpenAi_ChatRes)
  
  implicit val jf_oai_req_msg = jsonFormat2(OpenAi_ChatReqMsg)
  implicit val jf_oai_req = jsonFormat2(OpenAi_ChatReq)
}

class OpenAi(uri:String) extends AiProvider {
  import OpenAi_Json._

  val openAiUri = OpenAiURI(uri)

  override def getTimeout():Long = openAiUri.timeout
  override def getRetry():Int = openAiUri.retry
  
  def ask(question:String,model:Option[String],system:Option[String] = None,
          timeout:Long = getTimeout(),retry:Int = getRetry()):Try[Ai] = {

    val url = s"https://api.openai.com/v1/chat/completions"
    val modelReq = model.getOrElse(openAiUri.model.getOrElse("gpt-4o"))
    val body = OpenAi_ChatReq(
      model = modelReq,
      messages = Seq(
        OpenAi_ChatReqMsg("system",system.getOrElse("")),
        OpenAi_ChatReqMsg("user",question)
      )
    ).toJson.compactPrint
         
    log.info(s"asking: ${modelReq}: '${question.take(32).replaceAll("\n","\\\\n")}...(${question.size})' -> ${url}")    

    withRetry(
      {
        val r = requests.post(
          url = url,
          headers = Seq(
            "Content-Type" -> "application/json", 
            "Authorization" -> s"Bearer ${openAiUri.apiKey}"
          ),
          data = body,
          readTimeout = timeout.toInt,
          connectTimeout = timeout.toInt
        )      
        log.debug(s"${body}: ${r}")

        val chatRes = r.text().parseJson.convertTo[OpenAi_ChatRes]
              
        Ai(
          question = question,
          answer = Some(chatRes.choices.head.message.content),        
          oid = Some(Providers.OPEN_AI),
          model = Some(chatRes.model)
        )
      }, 
      s"ask: '${question.take(32)}...'"
    )(timeout, retry)
  }

  def prompt(prompt:Prompt,model:Option[String],system:Option[String] = None,
            timeout:Long = getTimeout(),retry:Int = getRetry()):Try[Prompt] = {

    val url = s"https://api.openai.com/v1/chat/completions"
    val modelReq = model.getOrElse(openAiUri.model.getOrElse("gpt-4o"))
    val body = OpenAi_ChatReq(
      model = modelReq,
      messages = prompt.messages.map( p => OpenAi_ChatReqMsg(p.role,p.content))
    ).toJson.compactPrint
          
    val promptSize = prompt.messages.map(_.content.size).sum
    log.info(s"prompt: [${modelReq},${prompt.messages.size},${promptSize}]' -> ${url}")

    withRetry(
      {
        val r = requests.post(
          url = url,
          headers = Seq(
            "Content-Type" -> "application/json", 
            "Authorization" -> s"Bearer ${openAiUri.apiKey}"
          ),
          data = body,
          readTimeout = timeout.toInt,
          connectTimeout = timeout.toInt
        )
        log.debug(s"${body}: ${r}")

        val chatRes = r.text().parseJson.convertTo[OpenAi_ChatRes]
              
        Prompt(
          messages = chatRes.choices.map(c => PromptMessage(role = c.message.role, content = c.message.content)),
          oid = prompt.oid,
          model = Some(chatRes.model),
          ts = System.currentTimeMillis(),
          ts0 = prompt.ts0,
          tags = prompt.tags,
          meta = prompt.meta
        )
      }, 
      s"prompt: [${prompt.messages.size} msgs, ${promptSize} chars]"
    )(timeout, retry)
  }
}