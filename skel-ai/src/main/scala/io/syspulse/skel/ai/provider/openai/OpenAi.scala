package io.syspulse.skel.ai.provider.openai

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger

import java.io.BufferedReader
import java.io.InputStreamReader

import akka.stream.scaladsl.Source
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpRequest,HttpResponse,HttpEntity,ContentTypes}
import akka.http.scaladsl.model.{HttpMethods,StatusCodes}
import akka.http.scaladsl.model.{StatusCodes,HttpEntity,ContentTypes}
import akka.actor.ActorSystem
import akka.util.ByteString
import akka.http.scaladsl.model.headers.RawHeader

import os._
import io.jvm.uuid._

import spray.json._
import spray.json.RootJsonFormat
import DefaultJsonProtocol._

import io.syspulse.skel.util.Retry
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.ai.Ai
import io.syspulse.skel.ai.core.Providers
import io.syspulse.skel.ai.core.OpenAiURI
import io.syspulse.skel.ai.Chat
import io.syspulse.skel.ai.ChatMessage
import io.syspulse.skel.ai.provider.AiProvider
import io.syspulse.skel.ai.core.AiTool
import io.syspulse.skel.ai.core.AiURI

// {
//         "role": "user",
//         "content": [
//           {
//             "type": "text",
//             "text": "Parse the data from this image and create a markdown table with statistics"
//           },
//           {
//             "type": "image_url",
//             "image_url": {
//               "url": "https://pbs.twimg.com/media/ABC123.jpg:orig"
//             }
//           }
//         ]
//       }

case class OpenAi_ImageUrl(
  url:String
)

case class OpenAi_ContentItem(
  `type`:String,
  text:Option[String] = None,
  image_url:Option[OpenAi_ImageUrl] = None
)

case class OpenAi_Msg(
  role:String,
  content:Seq[OpenAi_ContentItem]
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
  prompt_tokens: Int,
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

  tools:Option[Seq[AiTool]] = None  
)

case class OpenAi_OutputContent(
  `type`:String,
  text:String,
  annotations:Option[Seq[JsValue]]
)

case class OpenAi_Output (
  `type`:String,
  id:String,
  status:Option[String],
  role:Option[String],
  content:Option[Seq[OpenAi_OutputContent]]
)


case class OpenAi_ResponsesRes(
  id:String,
  `object`:String,
  created_at: Long,
  status:Option[String], // OpenRouter may not have status
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
  store:Option[Boolean] = None, // Whether to store the generated model response for later retrieval via API.
  
  stream:Option[Boolean] = None,

  max_output_tokens:Option[Int] = None,
  modalities:Option[Seq[String]] = None,
  temperature:Option[Double] = None,
  top_p:Option[Double] = None,
  
  truncation:Option[String] = None,

  user:Option[String] = None,

  tools:Option[Seq[AiTool]] = None,

  //text:Option[] = None,
  //reasoning:Option[] = None,
)

case class OpenAi_StreamEvent(
  event:String,
)

case class OpenAi_EventResponseCompleted(
  `type`:String,
  sequence_number:Option[Int] = None,
  response:OpenAi_ResponsesRes
)

object OpenAi_Json extends JsonCommon { 
  import spray.json.{JsString, JsArray, JsValue, JsonFormat, DeserializationException, deserializationError}
  
  implicit val jf_oai_tool = jsonFormat5(AiTool)

  implicit val jf_oai_image_url = jsonFormat1(OpenAi_ImageUrl)
  implicit val jf_oai_content_item = jsonFormat3(OpenAi_ContentItem)
  
  implicit object OpenAi_MsgFormat extends RootJsonFormat[OpenAi_Msg] {
    def write(msg: OpenAi_Msg) = {
      JsObject(
        "role" -> JsString(msg.role),
        "content" -> JsArray(msg.content.map(_.toJson).toVector)
      )
    }
    
    def read(value: JsValue) = {
      value.asJsObject.getFields("role", "content") match {
        case Seq(JsString(role), JsString(content)) =>
          // Handle legacy string format in responses
          OpenAi_Msg(role, Seq(OpenAi_ContentItem("text", Some(content), None)))
        case Seq(JsString(role), JsArray(contentItems)) =>
          // Handle array format
          val items = contentItems.map(_.convertTo[OpenAi_ContentItem])
          OpenAi_Msg(role, items)
        case _ =>
          deserializationError("OpenAi_Msg expected with 'role' and 'content' fields")
      }
    }
  }
  
  implicit val jf_oai_cho = jsonFormat3(OpenAi_Choices)
  implicit val jf_oai_usg = jsonFormat3(OpenAi_ChatUsage)
  implicit val jf_oai_chat_res = jsonFormat7(OpenAi_ChatRes)  
  implicit val jf_oai_req = jsonFormat15(OpenAi_CompletionReq)

  implicit val jf_oai_input = jsonFormat3(OpenAi_Input)
  implicit val jf_oai_output_content = jsonFormat3(OpenAi_OutputContent)
  implicit val jf_oai_output = jsonFormat5(OpenAi_Output)
  implicit val jf_oai_res = jsonFormat13(OpenAi_ResponsesReq)  
  implicit val jf_oai_res_res = jsonFormat10(OpenAi_ResponsesRes)  

  implicit val jf_oai_stream_event = jsonFormat1(OpenAi_StreamEvent)
  implicit val jf_oai_event_response_completed = jsonFormat3(OpenAi_EventResponseCompleted)

}

abstract class OpenAiLike(uri:AiURI) extends AiProvider {
  import OpenAi_Json._

  val aiUri:AiURI = uri

  def getUri():AiURI = aiUri
  override def getTimeout():Long = aiUri.timeout
  override def getRetry():Int = aiUri.retry
  override def getModel():Option[String] = aiUri.getModel()

  def getResponseAnswer(response:OpenAi_ChatRes):Option[String] = {
    if(response.choices.isEmpty) 
      None 
    else {
      val content = response.choices.head.message.content
      Some(content.flatMap(_.text).mkString("\n"))
    }
  }

  override def ask(question:String,model:Option[String],system:Option[String] = None,
          timeout:Long = getTimeout(),retry:Int = getRetry(),
          tools:Seq[AiTool] = Seq.empty,
          images:Seq[String] = Seq.empty
      ):Try[Ai] = {
    askWithImages(question, model, system, timeout, retry, tools, images)
  }
  
  def askWithImages(question:String,model:Option[String],system:Option[String],
          timeout:Long,retry:Int,
          tools0:Seq[AiTool],
          images0:Seq[String]
      ):Try[Ai] = {

    val url = s"${aiUri.apiUrl}/v1/chat/completions"
    val modelReq = model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    val systemPrompt = system.orElse(aiUri.system).getOrElse("")
    val tools = aiUri.getTools() ++ tools0

    val userContent = Seq(
      OpenAi_ContentItem("text", Some(question), None)
    ) ++ images0.map(url => 
      OpenAi_ContentItem("image_url", None, Some(OpenAi_ImageUrl(url)))
    )
    
    val body = OpenAi_CompletionReq(
      model = modelReq,
      messages = Seq(
        OpenAi_Msg("system", Seq(OpenAi_ContentItem("text", Some(systemPrompt), None))),
        OpenAi_Msg("user", userContent)
      ),
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_completion_tokens = aiUri.maxTokens,
      tools = if(tools.nonEmpty) Some(tools) else None
    ).toJson.compactPrint
  
    log.debug(s"body=${body} -> ${url}")
    log.info(s"model=${modelReq},sys=[${systemPrompt.size}]/q=[${question.size}]: '${question.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")  
    
    Retry.withRetry(
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
        log.debug(s"res: ${body}: ${r}")

        val chatRes = r.text().parseJson.convertTo[OpenAi_ChatRes]
        val answer = getResponseAnswer(chatRes)
              
        Ai(
          question = question,
          answer = answer,
          oid = Some(Providers.OPEN_AI),
          model = Some(aiUri.getModel(chatRes.model))
        )
      }, 
      s"ask: '${question.take(32)}...'"
    )(timeout, retry)(log)
  }

  def chat(chat:Chat,model:Option[String],system:Option[String] = None,
           timeout:Long = getTimeout(),retry:Int = getRetry(),tools0:Seq[AiTool] = Seq.empty):Try[Chat] = {

    val url = s"${aiUri.apiUrl}/v1/chat/completions"
    val modelReq = model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    val systemPrompt = system.orElse(aiUri.system)
    val tools = aiUri.getTools() ++ tools0

    val messages = chat.messages.map( p => {
      p.role.trim match {
        case "system" if(systemPrompt.isDefined) => 
          // overwrite system prompt if needed
          OpenAi_Msg("system", Seq(OpenAi_ContentItem("text", Some(systemPrompt.get), None)))
        case _ => 
          OpenAi_Msg(p.role, Seq(OpenAi_ContentItem("text", Some(p.content), None)))
      }
    })
    
    val body = OpenAi_CompletionReq(
      model = modelReq,
      messages = messages,
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_completion_tokens = aiUri.maxTokens,
      tools = if(tools.nonEmpty) Some(tools) else None
    ).toJson.compactPrint
          
    log.debug(s"body=${body}")

    val chatSize = messages.map(_.content.flatMap(_.text).map(_.size).sum).sum
    log.info(s"model=${modelReq},sys=[${systemPrompt.map(_.size).getOrElse(-1)}]/q=[${messages.size}] -> ${url}")

    Retry.withRetry(
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
          messages = chat.messages ++ chatRes.choices.map(c => {
            val content = c.message.content.flatMap(_.text).mkString("\n")
            ChatMessage(role = c.message.role, content = content)
          }),
          oid = chat.oid,
          model = Some(aiUri.getModel(chatRes.model)),
          ts = System.currentTimeMillis(),
          ts0 = chat.ts0,
          tags = chat.tags,
          meta = chat.meta
        )
      }, 
      s"chat: [${chat.messages.size} msgs, ${chatSize} chars]"
    )(timeout, retry)(log)
  }

  import io.syspulse.skel.FutureAwaitable
  //import io.syspulse.skel.FutureAwaitable._
  
  def prompt(ai:Ai,system:Option[String] = None,
            timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty):Try[Ai] = {    
    val f = promptAsync(ai,system,timeout,retry,tools)(scala.concurrent.ExecutionContext.Implicits.global)
    FutureAwaitable.awaitTry(f)(timeout)
  }
  
  def promptAsync(ai:Ai,system:Option[String] = None,
            timeout:Long = getTimeout(),retry:Int = getRetry(),tools0:Seq[AiTool] = Seq.empty)(implicit ec: ExecutionContext):Future[Ai] = {

    val url = s"${aiUri.apiUrl}/v1/responses"
    val modelReq = ai.model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    val systemPrompt = system.orElse(aiUri.system)
    val tools = aiUri.getTools() ++ tools0
    
    val body = OpenAi_ResponsesReq(
      model = modelReq,
      input = Seq(
        OpenAi_Input("user",ai.question)
      ),
      instructions = systemPrompt,
      previous_response_id = ai.xid,
      store = aiUri.getOptions().get("store").map(_.toBoolean),
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_output_tokens = aiUri.maxTokens,
      tools = if(tools.nonEmpty) Some(tools) else None
    ).toJson.compactPrint

    log.debug(s"body=${body}")         
    log.info(s"model=${modelReq},sys=[${systemPrompt.map(_.size).getOrElse(-1)}]/q=[${ai.question.size}]: '${ai.question.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")

    Future{ 
      Retry.withRetrying(
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

          val answer = getResponseAnswer(res)
          ai.copy(
            answer = answer,
            model = Some(aiUri.getModel(res.model)),
            xid = Some(res.id)
          )
        }, 
        s"responses: '${ai.question.take(32)}...'"
      )(timeout, retry)(log)
    }
  }
  

  def promptStream(ai: Ai, onEvent: (String) => Unit, instructions: Option[String] = None,timeout: Long = getTimeout(), retry: Int = getRetry(),tools:Seq[AiTool] = Seq.empty): Try[Ai] = {                    
    val f = promptStreamAsync(ai,onEvent,instructions,timeout,retry,tools)(scala.concurrent.ExecutionContext.Implicits.global)
    FutureAwaitable.awaitTry(f)(timeout)
  }

  def getResponseAnswer(response:OpenAi_ResponsesRes):Option[String] = {
    response.output.flatMap(o => {
      o.`type` match {
        case "message" => o.content.flatMap(_.headOption.map(_.text))
        case _ => None
      }
    }) match {
      case aa:Seq[String] => Some(aa.mkString("\n"))
      case _ => None
    }
  }

  def promptStreamAsync(ai:Ai,onEvent: (String) => Unit,instructions:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools0:Seq[AiTool] = Seq.empty)(implicit ec: ExecutionContext):Future[Ai] = {
    
    val url = s"${aiUri.apiUrl}/v1/responses"
    val modelReq = ai.model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    val systemPrompt = if( ! ai.xid.isDefined) instructions.orElse(aiUri.system) else None

    val tools = aiUri.getTools() ++ tools0

    val body = OpenAi_ResponsesReq(
      model = modelReq,
      input = Seq(
        OpenAi_Input("user", ai.question)
      ),
      stream = Some(true),
      instructions = systemPrompt,
      previous_response_id = ai.xid,
      store = aiUri.getOptions().get("store").map(_.toBoolean),
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_output_tokens = aiUri.maxTokens,
      tools = if(tools.nonEmpty) Some(tools) else None
    ).toJson.compactPrint
       
    log.debug(s"body=${body} -> ${url}")
    log.info(s"model=${modelReq},sys=${systemPrompt.map(_.size).getOrElse(-1)},tools=${tools}],q=[${ai.question.size}]: '${ai.question.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")
    
    Future {
      var a:Option[Ai] = None

      Retry.withRetrying({
        val r = requests.post.stream(
          url = url,
          headers = Seq(
            "Content-Type" -> "application/json", 
            "Authorization" -> s"Bearer ${aiUri.apiKey}",
            "Accept" -> "text/event-stream"
          ),
          data = body,
          readTimeout = timeout.toInt,
          connectTimeout = timeout.toInt
        )
        
        r.readBytesThrough(is => {
          val reader = new BufferedReader(new InputStreamReader(is))
          Iterator.continually(reader.readLine())
            .takeWhile(_ != null)
            .foreach { line =>
              if(line.nonEmpty) {

                log.trace(s"<- ${line}")

                line match {                
                  case s"data: ${data}" =>
                    log.debug(s"data: ${data}")

                    if(data.startsWith("""{"type":"response.completed"""")) {
                      val res = data.parseJson.convertTo[OpenAi_EventResponseCompleted]
                      
                      val answer = getResponseAnswer(res.response)

                      a = Some(ai.copy(
                        answer = answer,
                        model = Some(aiUri.getModel(res.response.model)),
                        xid = Some(res.response.id)
                      ))
                    } else {
                      onEvent(line)
                    }

                  case s"event: ${event}" => 
                    log.debug(s"event: ${event}")
                  case _ => 
                    log.warn(s"unknown rsp: '${line}'")

                }
              }
            }
            
        })

      }, s"responses stream: '${ai.question.take(32)}...'")(timeout, retry)(log)      

      a match {
        case Some(a) => a
        case None => throw new Exception(s"response.completed not received: ${ai.xid}")
      }
    }
  }

  def askStream(ai:Ai,
    instructions:Option[String] = None,
    onEvent: (String) => Unit = (s) => {},
    onData: (String) => Unit = (s) => {},
    onError: (String) => Unit = (s) => {},
    onDone: () => Unit = () => {},
    timeout:Long = getTimeout(),
    retry:Int = getRetry(),
    tools0:Seq[AiTool] = Seq.empty)(implicit ec: ExecutionContext,sys: ActorSystem): Source[ServerSentEvent, Any] = {
    
    val url = s"${aiUri.apiUrl}/v1/responses"
    // val url = s"http://localhost:8081/"
    val modelReq = ai.model.getOrElse(OpenAiURI.DEFAULT_MODEL)
    val systemPrompt = if( ! ai.xid.isDefined) instructions.orElse(aiUri.system) else None

    val tools = aiUri.getTools() ++ tools0

    val body = OpenAi_ResponsesReq(
      model = modelReq,
      input = Seq(
        OpenAi_Input("user", ai.question)
      ),
      stream = Some(true),
      instructions = systemPrompt,
      previous_response_id = ai.xid,
      store = aiUri.getOptions().get("store").map(_.toBoolean),
      temperature = aiUri.temperature,
      top_p = aiUri.topP,
      max_output_tokens = aiUri.maxTokens,
      tools = if(tools.nonEmpty) Some(tools) else None
    ).toJson.compactPrint
       
    log.debug(s"body=${body} -> ${url}")
    log.info(s"model=${modelReq},sys=${systemPrompt.map(_.size).getOrElse(-1)},tools=${tools},q=[${ai.question.size}]: '${ai.question.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")
    
    val httpRequest = HttpRequest(
          method = HttpMethods.POST,
          uri = url,
          entity = HttpEntity(ContentTypes.`application/json`, body),
          headers = Seq(
            RawHeader("Authorization", s"Bearer ${aiUri.apiKey}"),
            RawHeader("Accept", "text/event-stream")
          )
        )
      
    // Create a source that makes the HTTP request and parses SSE events directly
    // This ensures that when the HTTP connection to HttpServerAkka ends, the stream to client also ends
    val r = Source.future(
      Http()(sys).singleRequest(httpRequest)
        .recover {
          case e: Exception =>
            log.error(s"request failed -> '${url}': ${e.getMessage}")
            onError(s"HTTP error: ${e.getMessage}")
            HttpResponse(
              status = StatusCodes.InternalServerError,
              entity = HttpEntity(s"Error: ${e.getMessage}")
            )
        }
    )
    .flatMapConcat { response =>
      if (response.status == StatusCodes.OK) {
        response.entity.dataBytes
          .via(akka.stream.scaladsl.Framing.delimiter(ByteString("\n"), maximumFrameLength = 8192))
          .map(_.utf8String.trim)
          .filter(_.nonEmpty)
          .map { line => {
            log.info(s"LINE >>>>>>>>'${line}'")

            // Parse SSE format: data: <json>
            if (line.startsWith("data: ")) {
              val data = line.substring(6)
              if (data.nonEmpty) {
                onData(data)
                ServerSentEvent(data = data)
              } else {
                onDone()
                ServerSentEvent("", eventType = Some("done"))
              }
            } else if (line.startsWith("event: ")) {
              // Handle event type
              val eventType = line.substring(7)
              onEvent(eventType)
              ServerSentEvent("", eventType = Some(eventType))
            } else if (line.startsWith("id: ")) {
              // Handle event id
              val id = line.substring(4)
              onEvent(id)
              ServerSentEvent("", id = Some(id))
            } else {
              // Forward other lines as-is
              log.info(s"---------------------------->'${line}'")
              onData(line)
              ServerSentEvent(data = line)
            }
          }}
          .recover {
            case e: Exception =>
              log.error(s"Stream parsing failed: ${e.getMessage}")
              onError(s"parsing failed: ${e.getMessage}")
              ServerSentEvent(s"${e.getMessage}", eventType = Some("error"))
          }
      } else {
        val err = s"${response.status}"
        onError(err)
        Source.single(ServerSentEvent(err, eventType = Some("error")))
      }
    }
    r    
  } 
}

class OpenAi(uri:OpenAiURI) extends OpenAiLike(uri)
