package io.syspulse.skel.ai.source.openai

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
import io.syspulse.skel.ai.source.Sources

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

class OpenAi(uri:String) {
  import OpenAi_Json._

  val openAiUri = OpenAiURI(uri)

  val log = Logger(s"${this}")

  def chat(question:String,model:Option[String]):Try[Ai] = {
    val system = ""
    
    val url = s"https://api.openai.com/v1/chat/completions"
    val modelReq = model.getOrElse(openAiUri.model)
    val body = OpenAi_ChatReq(
      model = modelReq,
      messages = Seq(
        OpenAi_ChatReqMsg("system",system),
        OpenAi_ChatReqMsg("user",question)
      )
    ).toJson.compactPrint
          
    log.info(s"asking: ${modelReq}: '${question.take(32).replaceAll("\n","\\\\n")}...(${question.size})' -> ${url}")
          
    try {
      val r = requests.post(
        url = url,
        headers = Seq("Content-Type" -> "application/json", "Authorization" -> s"Bearer ${openAiUri.apiKey}"),
        data = body
      )
      log.debug(s"${question}: ${r}")

      val chatRes = r.text().parseJson.convertTo[OpenAi_ChatRes]
            
      Success(Ai(
        question = question,
        answer = Some(chatRes.choices.head.message.content),        
        oid = Some(Sources.OPEN_AI),
        model = Some(chatRes.model)
      ))
    } catch {
      case e:Exception =>
        log.error(s"failed to get answer: '${question}'",e)
        Failure(e)
    }
  }
}