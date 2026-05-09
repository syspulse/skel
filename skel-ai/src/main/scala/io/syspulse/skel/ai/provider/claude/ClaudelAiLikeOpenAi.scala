package io.syspulse.skel.ai.provider.claude

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.util.concurrent.TimeUnit

import akka.stream.scaladsl.Source
import akka.stream.SystemMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, HttpEntity, ContentTypes}
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.actor.ActorSystem
import akka.util.ByteString
import akka.http.scaladsl.model.headers.RawHeader

import spray.json._
import spray.json.DefaultJsonProtocol._

import io.syspulse.skel.util.Retry
import io.syspulse.skel.ai.Ai
import io.syspulse.skel.ai.Chat
import io.syspulse.skel.ai.ChatMessage
import io.syspulse.skel.ai.provider.AiProvider
import io.syspulse.skel.ai.core.{AiTool, ClaudeURI, Providers}

/** Anthropic Messages API (`POST /v1/messages`). */
trait ClaudeAiLikeOpenAi extends AiProvider with ClaudeAiProto {

  // Resolve diamond overrides: AiProvider vs ClaudeAiProto
  override def getTimeout(): Long = getUri().timeout
  override def getRetry(): Int = getUri().retry
  override def getModel(): Option[String] = getUri().getModel()
  
  private def messagesBody(
    model: String,
    messages: Vector[JsObject],
    system: Option[String],
    stream: Boolean,
    tools: Seq[AiTool],
    outputType: Option[String]
  ): String = {
    val sysField = system.map(s => "system" -> JsString(s))
    val streamField = if (stream) Some("stream" -> JsBoolean(true)) else None
    val tempField = getUri().temperature.map(t => "temperature" -> JsNumber(t))
    val topPField = getUri().topP.map(t => "top_p" -> JsNumber(t))
    val maxTok = Some("max_tokens" -> JsNumber(getUri().maxTokens.getOrElse(defaultMaxTokens)))
    if (outputType.isDefined) {
      log.warn("Claude Messages API: outputType is not mapped to a request field in this build; ignoring")
    }
    if (tools.nonEmpty) {
      log.warn("Claude Messages API: tools are not mapped from AiTool in this build; omitting tools")
    }
    val fields = Vector(
      Some("model" -> JsString(model)),
      Some("messages" -> JsArray(messages: _*)),
      maxTok,
      sysField,
      streamField,
      tempField,
      topPField
    ).flatten
    JsObject(fields: _*).compactPrint
  }

  private def parseAssistantMessage(js: JsValue): (Option[String], Option[String], Option[String]) = {
    val obj = js.asJsObject
    val id = obj.fields.get("id").map(_.convertTo[String])
    val model = obj.fields.get("model").map(_.convertTo[String])
    val text = obj.fields.get("content") match {
      case Some(JsArray(items)) =>
        Some(
          items
            .map(_.asJsObject)
            .flatMap { o =>
              o.fields.get("type") match {
                case Some(JsString("text")) =>
                  o.fields.get("text").map(_.convertTo[String])
                case _ => None
              }
            }
            .mkString("\n")
        )
      case Some(JsString(s)) => Some(s)
      case _ => None
    }
    (text, id, model)
  }

  private def postMessagesOnce(
    model: String,
    messages: Vector[JsObject],
    system: Option[String],
    tools: Seq[AiTool],
    outputType: Option[String],
    timeout: Long
  ): Future[Ai] = {
    val url = s"${getUri().apiUrl}/v1/messages"
    val body = messagesBody(model, messages, system, stream = false, tools, outputType)

    log.debug(s"body=${body} -> ${url}")
    log.info(s"model=${model},sys=[${system.size}]/q=[${messages.size}] -> ${url}")

    httpRequest(url, body, baseHeaders, timeout).flatMap { resp =>
      if (resp.status == StatusCodes.OK) {
        readResponseBody(resp).map { raw =>
          
          log.trace(s"<- ${url}: '${raw}'")

          val js = raw.parseJson
          val (answer, msgId, mdl) = parseAssistantMessage(js)
          Ai(
            question = "",
            answer = answer,
            oid = Some(Providers.CLAUDE),
            model = mdl.orElse(Some(model)).map(getUri().getModel)
          ).copy(xid = msgId)
        }
      } else {
        readResponseBody(resp).flatMap { err =>
          Future.failed(new Exception(s"HTTP ${resp.status}: $err"))
        }
      }
    }
  }

  private def roleContentJson(role: String, text: String, images: Seq[String]): JsObject = {
    val content: JsValue =
      if (role == "user" && images.nonEmpty) userContentBlocks(text, images)
      else JsString(text)
    JsObject("role" -> JsString(role), "content" -> content)
  }

  override def ask(
    question: String,
    model: Option[String],
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  ): Try[Ai] = {
    val modelReq = model.getOrElse(ClaudeURI.DEFAULT_MODEL)
    val sys = system.orElse(getUri().system)
    val msgs = Vector(roleContentJson("user", question, images))
    Retry.withRetry(
      {
        val ai0 = Await.result(
          postMessagesOnce(modelReq, msgs, sys, getUri().getTools() ++ tools, outputType, timeout),
          Duration(timeout, TimeUnit.MILLISECONDS)
        )
        ai0.copy(question = question)
      },
      s"claude ask: '${question.take(32)}...'"
    )(timeout, retry)(log)
  }

  override def chat(
    chat0: Chat,
    model: Option[String],
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  ): Try[Chat] = {
    val modelReq = model.getOrElse(ClaudeURI.DEFAULT_MODEL)
    val sysPrompt = system.orElse(getUri().system)
    val toolsAll = getUri().getTools() ++ tools

    val chatSystem =
      chat0.messages.filter(_.role.trim == "system").map(_.content).filter(_.nonEmpty).mkString("\n").trim
    val mergedSys = sysPrompt.orElse(if (chatSystem.nonEmpty) Some(chatSystem) else None)
    val tail = chat0.messages.filter(_.role.trim != "system")

    val msgs = tail.zipWithIndex.flatMap { case (m, idx) =>
      val r = m.role.trim
      val rl = r.toLowerCase
      if (m.content.isBlank && rl != "assistant") None
      else {
        val imgs = if (idx == tail.size - 1 && rl == "user") images else Seq.empty
        val role = if (rl == "assistant") "assistant" else "user"
        Some(roleContentJson(role, m.content, imgs))
      }
    }.toVector

    if (msgs.isEmpty) {
      Failure(new IllegalArgumentException("no user/assistant messages to send"))
    } else {
      Retry.withRetry(
        {
          val aiRes = Await.result(
            postMessagesOnce(modelReq, msgs, mergedSys, toolsAll, outputType, timeout),
            Duration(timeout, TimeUnit.MILLISECONDS)
          )
          Chat(
            messages = chat0.messages ++ Seq(ChatMessage("assistant", aiRes.answer.getOrElse(""))),
            oid = chat0.oid,
            model = aiRes.model.orElse(Some(modelReq)),
            ts = System.currentTimeMillis(),
            ts0 = chat0.ts0,
            tags = chat0.tags,
            meta = chat0.meta
          )
        },
        s"claude chat: [${chat0.messages.size} msgs]"
      )(timeout, retry)(log)
    }
  }

  private def doPromptFutureFixed(
    ai: Ai,
    system: Option[String],
    timeout: Long,
    retry: Int,
    tools0: Seq[AiTool],
    images: Seq[String],
    outputType: Option[String]
  )(implicit ec: ExecutionContext): Future[Ai] = {
    val modelReq = ai.model.getOrElse(ClaudeURI.DEFAULT_MODEL)
    val sys = system.orElse(getUri().system)
    val tools = getUri().getTools() ++ tools0
    val msgs = Vector(roleContentJson("user", ai.question, images))
    def once(): Future[Ai] =
      postMessagesOnce(modelReq, msgs, sys, tools, outputType, timeout).map { r =>
        ai.copy(
          answer = r.answer,
          model = r.model.orElse(Some(modelReq)).map(getUri().getModel),
          xid = r.xid.orElse(ai.xid),
          oid = Some(Providers.CLAUDE)
        )
      }
    Retry.withRetryFuture(once(), s"claude messages: '${ai.question.take(32)}...'")(retry, 3000)(log, ec)
  }

  override def prompt(
    ai: Ai,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  ): Try[Ai] = {
    import io.syspulse.skel.FutureUtil
    val f = doPromptFutureFixed(ai, system, timeout, retry, tools, images, outputType)(scala.concurrent.ExecutionContext.Implicits.global)
    FutureUtil.sync(f)(timeout)
  }

  override def promptAsync(
    ai: Ai,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools0: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  )(implicit ec: ExecutionContext): Future[Ai] =
    doPromptFutureFixed(ai, system, timeout, retry, tools0, images, outputType)

  override def messages(
    ai: Ai,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  ): Try[Ai] =
    prompt(ai, system, timeout, retry, tools, images, outputType)

  override def messagesAsync(
    ai: Ai,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools0: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  )(implicit ec: ExecutionContext): Future[Ai] =
    promptAsync(ai, system, timeout, retry, tools0, images, outputType)

  private def extractDeltaText(js: JsObject): Option[String] = {
    js.fields.get("type") match {
      case Some(JsString("content_block_delta")) =>
        js.fields.get("delta") match {
          case Some(d: JsObject) =>
            d.fields.get("type") match {
              case Some(JsString("text_delta")) =>
                d.fields.get("text").map(_.convertTo[String])
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  private def streamMessagesFuture(
    ai: Ai,
    onEvent: String => Unit,
    system: Option[String],
    timeout: Long,
    retry: Int,
    tools0: Seq[AiTool],
    images: Seq[String],
    outputType: Option[String]
  )(implicit ec: ExecutionContext): Future[Ai] = {
    val modelReq = ai.model.getOrElse(ClaudeURI.DEFAULT_MODEL)
    val sys = system.orElse(getUri().system)
    val tools = getUri().getTools() ++ tools0
    val body = messagesBody(modelReq, Vector(roleContentJson("user", ai.question, images)), sys, stream = true, tools, outputType)
    val url = s"${getUri().apiUrl}/v1/messages"
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = url,
      entity = HttpEntity(ContentTypes.`application/json`, body),
      headers = baseHeaders.map { case (k, v) => RawHeader(k, v) } :+ RawHeader("Accept", "text/event-stream")
    )
    
    log.debug(s"body=${body} -> ${url}")
    log.info(s"model=${modelReq},sys=[${sys.size}]/q=[${body.size}] -> ${url}")

    import java.util.concurrent.LinkedBlockingQueue
    import akka.stream.scaladsl.Sink

    def attempt(): Future[Ai] = {
      val resultQueue = new LinkedBlockingQueue[Option[Ai]](1)
      @volatile var acc = new StringBuilder
      @volatile var msgId: Option[String] = None
      @volatile var outModel: Option[String] = None

      Http()(httpSystem).singleRequest(req).flatMap { response =>
        if (response.status == StatusCodes.OK) {
          response.entity.dataBytes
            .via(akka.stream.scaladsl.Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536))
            .map(_.utf8String.trim)
            .filter(_.nonEmpty)
            .runWith(Sink.foreach { line =>
              log.trace(s"<- $line")
              line match {
                case s if s.startsWith("data:") =>
                  val data = s.stripPrefix("data:").trim
                  if (data.nonEmpty && data != "[DONE]") {
                    onEvent(line)
                    Try(data.parseJson) match {
                      case Success(js: JsObject) =>
                        js.fields.get("type") match {
                          case Some(JsString("message_start")) =>
                            js.fields.get("message").foreach {
                              case m: JsObject =>
                                m.fields.get("id").foreach(id => msgId = Some(id.convertTo[String]))
                                m.fields.get("model").foreach(md => outModel = Some(md.convertTo[String]))
                              case _ =>
                            }
                          case Some(JsString("content_block_delta")) =>
                            extractDeltaText(js).foreach(t => acc.append(t))
                          case Some(JsString("message_delta")) =>
                            js.fields.get("delta").foreach {
                              case d: JsObject =>
                                d.fields.get("stop_reason") // no-op
                              case _ =>
                            }
                          case Some(JsString("message_stop")) =>
                            val answer = if (acc.isEmpty) None else Some(acc.toString)
                            resultQueue.put(
                              Some(
                                ai.copy(
                                  answer = answer,
                                  model = outModel.orElse(Some(modelReq)).map(getUri().getModel),
                                  xid = msgId.orElse(ai.xid),
                                  oid = Some(Providers.CLAUDE)
                                )
                              )
                            )
                          case _ =>
                        }
                      case _ =>
                    }
                  }
                case s if s.startsWith("event:") =>
                  log.debug(s"'${s}'")
                case _ =>
                  onEvent(line)
              }
            })
            .map { _ =>
              Option(resultQueue.poll(timeout, TimeUnit.MILLISECONDS)) match {
                case Some(Some(a)) => a
                case _ =>
                  if (acc.nonEmpty) {
                    ai.copy(
                      answer = Some(acc.toString),
                      model = outModel.orElse(Some(modelReq)).map(getUri().getModel),
                      xid = msgId.orElse(ai.xid),
                      oid = Some(Providers.CLAUDE)
                    )
                  } else
                    throw new Exception("Claude stream: no assistant text and no message_stop")
              }
            }
        } else {
          readResponseBody(response).flatMap(err => Future.failed(new Exception(s"HTTP ${response.status}: $err")))
        }
      }
    }
    Retry.withRetryFuture(attempt(), s"claude messages stream: '${ai.question.take(32)}...'")(retry, 3000)(log, ec)
  }

  override def promptStream(
    ai: Ai,
    onEvent: String => Unit,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  ): Try[Ai] = {
    import io.syspulse.skel.FutureUtil
    val f = streamMessagesFuture(ai, onEvent, system, timeout, retry, tools, images, outputType)(scala.concurrent.ExecutionContext.Implicits.global)
    FutureUtil.sync(f)(timeout)
  }

  override def promptStreamAsync(
    ai: Ai,
    onEvent: String => Unit,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools0: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  )(implicit ec: ExecutionContext): Future[Ai] =
    streamMessagesFuture(ai, onEvent, system, timeout, retry, tools0, images, outputType)

  override def messagesStream(
    ai: Ai,
    onEvent: String => Unit,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  ): Try[Ai] =
    promptStream(ai, onEvent, system, timeout, retry, tools, images, outputType)

  override def messagesStreamAsync(
    ai: Ai,
    onEvent: String => Unit,
    system: Option[String] = None,
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools0: Seq[AiTool] = Seq.empty,
    images: Seq[String] = Seq.empty,
    outputType: Option[String] = None
  )(implicit ec: ExecutionContext): Future[Ai] =
    promptStreamAsync(ai, onEvent, system, timeout, retry, tools0, images, outputType)

  override def askStream(
    ai: Ai,
    instructions: Option[String] = None,
    onEvent: String => Unit = _ => {},
    onData: String => Unit = _ => {},
    onError: String => Unit = _ => {},
    onDone: () => Unit = () => {},
    timeout: Long = getTimeout(),
    retry: Int = getRetry(),
    tools: Seq[AiTool] = Seq.empty,
    outputType: Option[String] = None
  )(implicit ec: ExecutionContext, sys: ActorSystem): Source[ServerSentEvent, Any] = {
    val modelReq = ai.model.getOrElse(ClaudeURI.DEFAULT_MODEL)
    val sys0 = instructions.orElse(getUri().system)
    val body = messagesBody(modelReq, Vector(roleContentJson("user", ai.question, Seq.empty)), sys0, stream = true, getUri().getTools() ++ tools, outputType)
    val url = s"${getUri().apiUrl}/v1/messages"
    val httpReq = HttpRequest(
      method = HttpMethods.POST,
      uri = url,
      entity = HttpEntity(ContentTypes.`application/json`, body),
      headers = baseHeaders.map { case (k, v) => RawHeader(k, v) } :+ RawHeader("Accept", "text/event-stream")
    )
    Source
      .future(
        Http()(sys).singleRequest(httpReq).recover {
          case e: Exception =>
            log.error(s"claude stream request failed: ${e.getMessage}")
            onError(e.getMessage)
            HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(e.getMessage))
        }
      )
      .flatMapConcat { response =>
        if (response.status == StatusCodes.OK) {
          response.entity.dataBytes
            .via(akka.stream.scaladsl.Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536))
            .map(_.utf8String.trim)
            .filter(_.nonEmpty)
            .map { line =>
              if (line.startsWith("data:")) {
                val data = line.stripPrefix("data:").trim
                if (data.nonEmpty) {
                  onData(data)
                  ServerSentEvent(data)
                } else {
                  onDone()
                  ServerSentEvent("", eventType = Some("done"))
                }
              } else if (line.startsWith("event:")) {
                val ev = line.stripPrefix("event:").trim
                onEvent(ev)
                ServerSentEvent("", eventType = Some(ev))
              } else {
                onData(line)
                ServerSentEvent(line)
              }
            }
            .recover {
              case e: Exception =>
                onError(e.getMessage)
                ServerSentEvent(e.getMessage, eventType = Some("error"))
            }
        } else {
          val err = s"${response.status}"
          onError(err)
          Source.single(ServerSentEvent(err, eventType = Some("error")))
        }
      }
  }
}
