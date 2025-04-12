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
import io.syspulse.skel.ai.Chat
import io.syspulse.skel.ai.ChatMessage
import io.syspulse.skel.ai.provider.AiProvider

case class OpenAi_Msg(
  role:String,
  content:String
)

case class OpenAi_Input(
  role:String,
  content:String,
  `type`:Option[String] = None
)

case class OpenAi_Choices(
  index: Int,
  message: OpenAi_Msg,
  finish_reason: String
)

case class OpenAi_ChatUsage(
  chat_tokens: Int,
  completion_tokens: Int,
  total_tokens: Int,
)

case class OpenAi_ChatRes(
  id:String,
  `object`:String,
  created: Long,
  model:String,
  
  choices: Seq[OpenAi_Choices],

  usage: OpenAi_ChatUsage,
  system_fingerprint:Option[String]
)

case class OpenAi_CompletionReq(
  model:String,
  messages:Seq[OpenAi_Msg],

  stream:Option[Boolean] = None,

  max_completion_tokens:Option[Int] = None,
  modalities:Option[Seq[String]] = None,
  temperature:Option[Double] = None,
  top_p:Option[Double] = None,
  frequency_penalty:Option[Double] = None,
  presence_penalty:Option[Double] = None,
  seed:Option[Long] = None,
  n:Option[Int] = None,
  stop:Option[Seq[String]] = None,

  user:Option[String] = None,
  response_format:Option[String] = None,
)

case class OpenAi_OutputContent(
  `type`:String,
  text:String,
  annotations:Seq[String]
)

case class OpenAi_Output (
  `type`:String,
  id:String,
  status:String,
  role:String,
  content:Seq[OpenAi_OutputContent]
)


case class OpenAi_ResponsesRes(
  id:String,
  `object`:String,
  created_at: Long,
  status:String,
  error:Option[String],
  incomplete_details: Option[String],
  instructions: Option[String],
  max_output_tokens: Option[Int],
  model:String,
  
  output: Seq[OpenAi_Output],
)

case class OpenAi_ResponsesReq(
  model:String,
  input:Seq[OpenAi_Input],
  previous_response_id:Option[String] = None, // link conversation
  instructions:Option[String] = None, // system prompt
  store:Option[Boolean] = None, // True by default 
  
  stream:Option[Boolean] = None,

  max_output_tokens:Option[Int] = None,
  modalities:Option[Seq[String]] = None,
  temperature:Option[Double] = None,
  top_p:Option[Double] = None,
  
  truncation:Option[String] = None,

  user:Option[String] = None,

  //text:Option[] = None,
  //reasoning:Option[] = None,
)

object OpenAi_Json extends JsonCommon {  
  implicit val jf_oai_msg = jsonFormat2(OpenAi_Msg)
  implicit val jf_oai_cho = jsonFormat3(OpenAi_Choices)
  implicit val jf_oai_usg = jsonFormat3(OpenAi_ChatUsage)
  implicit val jf_oai_chat_res = jsonFormat7(OpenAi_ChatRes)  
  implicit val jf_oai_req = jsonFormat14(OpenAi_CompletionReq)

  implicit val jf_oai_input = jsonFormat3(OpenAi_Input)
  implicit val jf_oai_output_content = jsonFormat3(OpenAi_OutputContent)
  implicit val jf_oai_output = jsonFormat5(OpenAi_Output)
  implicit val jf_oai_res = jsonFormat12(OpenAi_ResponsesReq)  
  implicit val jf_oai_res_res = jsonFormat10(OpenAi_ResponsesRes)  
}

class OpenAi(uri:String) extends AiProvider {
  import OpenAi_Json._

  val aiUri = OpenAiURI(uri)

  override def getTimeout():Long = aiUri.timeout
  override def getRetry():Int = aiUri.retry
  
  def ask(question:String,model:Option[String],system:Option[String] = None,
          timeout:Long = getTimeout(),retry:Int = getRetry()):Try[Ai] = {

    val url = s"https://api.openai.com/v1/chat/completions"
    val modelReq = model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    val body = OpenAi_CompletionReq(
      model = modelReq,
      messages = Seq(
        OpenAi_Msg("system",system.getOrElse("")),
        OpenAi_Msg("user",question)
      ),
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_completion_tokens = aiUri.maxTokens,
    ).toJson.compactPrint
         
    log.info(s"asking: ${modelReq}: [sys=${system.size}/q=${question.size}]: '${question.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")    

    withRetry(
      {
        val r = requests.post(
          url = url,
          headers = Seq(
            "Content-Type" -> "application/json", 
            "Authorization" -> s"Bearer ${aiUri.apiKey}"
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

  def chat(chat:Chat,model:Option[String],system:Option[String] = None,
            timeout:Long = getTimeout(),retry:Int = getRetry()):Try[Chat] = {

    val url = s"https://api.openai.com/v1/chat/completions"
    val modelReq = model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    
    val body = OpenAi_CompletionReq(
      model = modelReq,
      messages = chat.messages.map( p => OpenAi_Msg(p.role,p.content)),
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_completion_tokens = aiUri.maxTokens,
    ).toJson.compactPrint
          
    val chatSize = chat.messages.map(_.content.size).sum
    log.info(s"chat: [${modelReq},${chat.messages.size},${chatSize}]' -> ${url}")

    withRetry(
      {
        val r = requests.post(
          url = url,
          headers = Seq(
            "Content-Type" -> "application/json", 
            "Authorization" -> s"Bearer ${aiUri.apiKey}"
          ),
          data = body,
          readTimeout = timeout.toInt,
          connectTimeout = timeout.toInt
        )
        log.debug(s"${body}: ${r}")

        val chatRes = r.text().parseJson.convertTo[OpenAi_ChatRes]
              
        Chat(
          messages = chat.messages ++ chatRes.choices.map(c => ChatMessage(role = c.message.role, content = c.message.content)),
          oid = chat.oid,
          model = Some(chatRes.model),
          ts = System.currentTimeMillis(),
          ts0 = chat.ts0,
          tags = chat.tags,
          meta = chat.meta
        )
      }, 
      s"chat: [${chat.messages.size} msgs, ${chatSize} chars]"
    )(timeout, retry)
  }

  def prompt(ai:Ai,model:Option[String],system:Option[String] = None,
          timeout:Long = getTimeout(),retry:Int = getRetry()):Try[Ai] = {

    val url = s"https://api.openai.com/v1/responses"
    val modelReq = model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    val body = OpenAi_ResponsesReq(
      model = modelReq,
      input = Seq(
        OpenAi_Input("user",ai.question)
      ),
      instructions = system,
      previous_response_id = ai.xid,
      store = aiUri.ops.get("store").map(_.toBoolean).orElse(Some(true)),
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_output_tokens = aiUri.maxTokens,
    ).toJson.compactPrint
         
    log.info(s"responses: ${modelReq}: [sys=${system.size}/q=${ai.question.size}]: '${ai.question.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")    

    withRetry(
      {
        val r = requests.post(
          url = url,
          headers = Seq(
            "Content-Type" -> "application/json", 
            "Authorization" -> s"Bearer ${aiUri.apiKey}"
          ),
          data = body,
          readTimeout = timeout.toInt,
          connectTimeout = timeout.toInt
        )      
        log.debug(s"${body}: ${r}")

        val res = r.text().parseJson.convertTo[OpenAi_ResponsesRes]
              
        ai.copy(
          answer = Some(res.output.head.content.head.text),
          model = Some(res.model),
          xid = Some(res.id)
        )
      }, 
      s"responses: '${ai.question.take(32)}...'"
    )(timeout, retry)
  }
}