package io.syspulse.skel.telegram

import akka.stream.scaladsl.{Source, Framing}
import akka.util.ByteString

import io.syspulse.skel.uri.TelURI
import spray.json._

/**
 * Source that reads messages from Telegram User API (tel://) via TDLight.
 * Use with pipeline feed tel://phone; output can be telegram:// (bot) or elsewhere.
 */
class FromTelegramUser(uri: String) extends TelegramUserClient {
  import TelegramJson._

  private val telUri = TelURI(uri)

  override def getApiId(): Int =
    telUri.apiId.getOrElse(throw new IllegalArgumentException("tel:// requires api_id or TELEGRAM_API_ID"))
  override def getApiHash(): String =
    telUri.apiHash.getOrElse(throw new IllegalArgumentException("tel:// requires api_hash or TELEGRAM_API_HASH"))
  override def getPhoneNumber(): String =
    telUri.phone.getOrElse(throw new IllegalArgumentException("tel:// requires phone or TELEGRAM_PHONE"))
  override def getSessionPath(): String =
    telUri.sessionPath

  /** Source as ByteString JSON lines (for pipeline compatibility with FromTelegram). */
  def byteSource(
    frameDelimiter: String = "\n",
    frameSize: Int = 8192
  ): Source[ByteString, _] = {
    val msgSource = source(Set.empty, telUri.freq, telUri.max)
    val jsonSource = msgSource.map(msg => ByteString(msg.toJson.compactPrint + "\n"))
    if (frameDelimiter.isEmpty) jsonSource
    else jsonSource.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }
}
