package io.syspulse.skel.ai.provider.claude

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.util.concurrent.TimeUnit

import akka.stream.scaladsl.Source
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
import io.syspulse.skel.FutureUtil
import io.syspulse.skel.ai.Ai

import io.syspulse.skel.ai.core.{AiTool, ClaudeURI, Providers}
import io.syspulse.skel.ai.core.AiURI

trait ClaudeAiProto  {

  def getUri(): AiURI

  protected val anthropicVersion = getUri().version.getOrElse("2023-06-01")
  protected val defaultMaxTokens = getUri().getOptions().get("max_tokens").map(_.toInt).getOrElse(8192)

  protected def baseHeaders: Seq[(String, String)] = {
    val h = Seq(
      "x-api-key" -> getUri().apiKey,
      "anthropic-version" -> anthropicVersion      
    )
    getUri().getOptions().get("anthropic-beta").map(b => h :+ ("anthropic-beta" -> b)).getOrElse(h)
  }

  def getTimeout(): Long = getUri().timeout
  def getRetry(): Int = getUri().retry
  def getModel(): Option[String] = getUri().getModel()

  protected def userContentBlocks(question: String, images: Seq[String]): JsArray = {
    val text = JsObject("type" -> JsString("text"), "text" -> JsString(question))
    val blocks = images.map { u =>
      JsObject(
        "type" -> JsString("image"),
        "source" -> JsObject("type" -> JsString("url"), "url" -> JsString(u))
      )
    }
    JsArray((text +: blocks).toVector)
  }
  
}
