package io.syspulse.skel.telegram

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.util.{Try, Success, Failure}
import scala.concurrent.ExecutionContext
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Source, Flow, Sink, RestartSource, Framing}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.util.ByteString

import spray.json._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.uri.TelegramURI

class FromTelegram(uri: String) extends TelegramClient {
  val telegramUri = TelegramURI(uri)
  import TelegramJson._

  def getChannels() = telegramUri.channels.toSet

  def source(
    frameDelimiter: String = DEF_FRAME_DELIMITER,
    frameSize: Int = DEF_FRAME_SIZE
  ): Source[ByteString, _] = {
    val s1 = source(
      telegramUri.botToken,
      telegramUri.channels.toSet,
      telegramUri.freq,
      telegramUri.timeout,
      telegramUri.max,
      telegramUri.allowedUpdates,
      telegramUri.buffer,
      frameDelimiter,
      frameSize
    )

    // Convert to ByteString JSON with newline
    val s2 = s1.map(msg =>
      ByteString(s"${msg.toJson.compactPrint}\n")
    )

    if (frameDelimiter.isEmpty)
      s2
    else
      s2.via(Framing.delimiter(
        ByteString(frameDelimiter),
        maximumFrameLength = frameSize,
        allowTruncation = true
      ))
  }
}
