package io.syspulse.skel.telegram

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.util.{Try, Success, Failure}
import scala.concurrent.ExecutionContext
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Flow}
import akka.http.scaladsl.Http
import akka.util.ByteString

import spray.json._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.uri.TelegramURI
import io.syspulse.skel.Ingestable

class ToTelegram[T <: Ingestable](uri: String)(implicit fmt:JsonFormat[T], as:ActorSystem) extends TelegramClient {
  private val log = Logger(s"${this}")
  import TelegramJson._
  
  val telegramUri = TelegramURI(uri)

  // Get the target chat ID from URI (first channel in the list)
  val targetChatIdRaw = telegramUri.channels.headOption.getOrElse {
    throw new IllegalArgumentException("required: chat_id")
  }

  def getChannels() = Set.empty[String]

  // Parse mode: None, Some("Markdown"), Some("MarkdownV2"), Some("HTML")
  val parseMode: Option[String] = None
  val disableNotification: Boolean = false

  // Request timeout (5 seconds)
  implicit val timeout = FiniteDuration(telegramUri.timeout, MILLISECONDS)

  // Resolve chat identifier (supports: numeric ID, @username, or chat title)
  // Note: Title resolution requires recent messages (uses getUpdates API)
  val targetChatId: String = resolveChannel(telegramUri.botToken, targetChatIdRaw)

  def sink(): Sink[T, Future[akka.Done]] = {
    Flow[T]
      .mapAsync(1) { obj =>
        log.debug(s"[send] ${obj} -> ${targetChatId}")
        // Extract text from the Ingestable object
        val text = obj match {
          case tm: TelegramMessage =>
            // If it's a TelegramMessage, use its text or convert to JSON
            tm.text.getOrElse(obj.toJson.compactPrint)
          case _ =>
            // For other types, convert to JSON string
            obj.toJson.compactPrint
        }

        // Truncate if too long (Telegram max message length is 4096 characters)
        val truncatedText = if (text.length > 4096) {
          text.take(4093) + "..."
        } else {
          text
        }

        //log.info(s"Sending message to ${targetChatId}: ${truncatedText.take(100)}...")

        // Send message to Telegram
        val futureResponse = sendMessage(
          telegramUri.botToken,
          targetChatId,
          truncatedText,
          parseMode,
          disableNotification
        )

        futureResponse.map { body =>
          val response = body.utf8String.parseJson.convertTo[TelegramSendMessageResponse]

          if (response.ok) {            
            akka.Done
          } else {
            val error = response.description.getOrElse("Unknown error")
            log.warn(s"Failed to send message: '${targetChatId}': ${error}")
            throw new Exception(s"Failed to send message: '${targetChatId}': ${error}")
          }
        }.recover {
          case e: Exception =>
            log.warn(s"Failed to send message: ${e.getMessage}", e)
            throw e
        }
      }
      .toMat(Sink.ignore)(akka.stream.scaladsl.Keep.right)
  }
}
