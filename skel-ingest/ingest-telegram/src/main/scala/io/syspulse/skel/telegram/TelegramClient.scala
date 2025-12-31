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

trait TelegramClient {
  private val log = Logger(s"${this}")

  import TelegramJson._

  val telegramUrlBase = "https://api.telegram.org/bot"

  val DEF_TIMEOUT = 30000L      // 30 seconds in ms
  val DEF_BUFFER = 1000
  val DEF_MAX = 100
  val DEF_FRAME_DELIMITER = "\n"
  val DEF_FRAME_SIZE = 8192

  implicit val as: ActorSystem = ActorSystem("ActorSystem-TelegramClient")
  implicit val ec: ExecutionContext = as.dispatcher

  def getChannels(): Set[String]

  // Check if a channel identifier is a numeric ID or a name
  def isNumericId(channel: String): Boolean = {
    channel.matches("-?\\d+")
  }

  // Resolve a single channel identifier to a usable chat ID
  // Handles: numeric IDs, @usernames, and chat titles
  def resolveChannel( botToken: String,channel: String)(implicit timeout_req: FiniteDuration): String = {
    if (isNumericId(channel)) {
      // Already a numeric ID - use directly
      log.debug(s"Using numeric chat ID: ${channel}")
      channel
    } else if (channel.startsWith("@")) {
      // Username with @ - Telegram API accepts this
      log.debug(s"Using username: ${channel}")
      channel
    } else {
      // Plain title - resolve using getUpdates (ONLY method available)
      log.info(s"Resolving chat title '${channel}' to numeric ID via getUpdates...")
      val resolved = resolveChannelNames(botToken, Set(channel))
      resolved.get(channel) match {
        case Some(chatId) =>
          log.info(s"Resolved '${channel}' -> ${chatId}")
          chatId
        case None =>
          log.error(s"Failed to resolve '${channel}'")          
          throw new IllegalArgumentException(s"Failed to resolve: '${channel}'")
      }
    }
  }

  // Resolve channel names to chat IDs by calling getUpdates
  // Returns a map of name -> chat_id for names that were found
  def resolveChannelNames( botToken: String, channels: Set[String])(implicit timeout_req: FiniteDuration): Map[String, String] = {
    // Separate numeric IDs from names
    val (ids, names) = channels.partition(isNumericId)

    if (names.isEmpty) {
      return Map.empty
    }

    log.info(s"[resolve] channels: ${names}")

    try {
      // Use long polling with offset=-1 to get only the latest updates
      // This prevents consuming updates that the main loop needs
      val futureResponse = getUpdates(botToken, offset = -1, timeout = 30000L, limit = 100)
      val body = Await.result(futureResponse, FiniteDuration(35000L, MILLISECONDS))

      val response = body.utf8String.parseJson.convertTo[TelegramGetUpdatesResponse]
      
      log.debug(s"[resovle] rsp='${body.utf8String}'")

      if (!response.ok) {
        log.warn(s"Failed to resolve channels: ${channels}: ${body.utf8String}")
        return Map.empty
      }

      if (response.result.isEmpty) {
        log.warn(s"Failed to resolve channels: ${names}")        
        return Map.empty
      }

      // Extract all chats from updates
      val chatMap = response.result.flatMap { update =>
        val apiMessage = update.message.orElse(update.channel_post).orElse(update.edited_channel_post)
        apiMessage.map { msg =>
          val chatTitle = msg.chat.title.getOrElse("")
          val chatId = msg.chat.id.toString
          (chatTitle, chatId)
        }
      }.toMap

      // Build name -> ID mapping for requested names
      val resolved = names.flatMap { name =>
        chatMap.get(name).map(id => (name, id))
      }.toMap

      // Log results
      resolved.foreach { case (name, id) =>
        log.info(s"[resolve] '$name' = $id")
      }

      val unresolved = names -- resolved.keySet
      if (unresolved.nonEmpty) {
        log.warn(s"Failed to resolve channels: ${unresolved}")        
      }

      resolved
    } catch {
      case e: Exception =>
        log.error(s"Failed to resolve channels: ${e.getMessage}", e)
        Map.empty
    }
  }

  // Make HTTP request to Telegram Bot API getChat
  // Returns information about a chat (type, title, username, etc.)
  def getChat( botToken: String, chatId: String)(implicit timeout_req: FiniteDuration): Future[ByteString] = {
    val url = s"${telegramUrlBase}${botToken}/getChat?chat_id=${chatId}"

    log.debug(s"[chat]: chat_id=${chatId}")

    Http()
      .singleRequest(HttpRequest(uri = url))
      .flatMap { res =>
        res.status match {
          case StatusCodes.OK =>
            res.entity.dataBytes.runReduce(_ ++ _)
          case _ =>
            val body = Await.result(
              res.entity.dataBytes.runReduce(_ ++ _),
              FiniteDuration(3000L, TimeUnit.MILLISECONDS)
            ).utf8String
            log.warn(s"Failed to get chat: ${chatId}: ${res.status}: body=${body}")
            // Return error response instead of throwing
            Future.successful(ByteString(s"""{"ok":false,"description":"${body}"}"""))
        }
      }
  }

  // Detect chat types for given chat IDs
  // Returns a map of chat_id -> (chat_type, chat_title)
  def detectChatTypes( botToken: String, chatIds: Set[String])(implicit timeout_req: FiniteDuration): Map[String, (String, String)] = {
    if (chatIds.isEmpty) {
      return Map.empty
    }

    log.debug(s"Detecting chat types: ${chatIds.mkString(", ")}")

    chatIds.flatMap { chatId =>
      try {
        val futureResponse = getChat(botToken, chatId)
        val body = Await.result(futureResponse, timeout_req)
        val response = body.utf8String.parseJson.convertTo[TelegramGetChatResponse]

        if (response.ok && response.result.isDefined) {
          val chat = response.result.get
          val chatType = chat.`type`
          val chatTitle = chat.title.orElse(chat.username).getOrElse(chatId)

          log.info(s"Chat '${chatTitle}' (${chatId}): ${chatType}")
          Some((chatId, (chatType, chatTitle)))
        } else {
          log.warn(s"Failed to detect chat type: '${chatId}': ${response.description.getOrElse("unknown error")}")
          None
        }
      } catch {
        case e: Exception =>
          log.warn(s"Failed to detect chat type: '${chatId}': ${e.getMessage}")
          None
      }
    }.toMap
  }

  // Make HTTP request to Telegram Bot API sendMessage
  // Sends a text message to a specified chat
  def sendMessage( botToken: String,chatId: String,text: String,parseMode: Option[String] = None, disableNotification: Boolean = false
  )(implicit timeout_req: FiniteDuration): Future[ByteString] = {
    import spray.json._

    // Build JSON payload
    val payload = Map(
      "chat_id" -> JsString(chatId),
      "text" -> JsString(text)
    ) ++
    parseMode.map(pm => Map("parse_mode" -> JsString(pm))).getOrElse(Map.empty) ++
    (if (disableNotification) Map("disable_notification" -> JsBoolean(true)) else Map.empty)

    val jsonPayload = JsObject(payload).compactPrint
    val url = s"${telegramUrlBase}${botToken}/sendMessage"

    log.debug(s"sendMessage: chat_id=${chatId}, text_length=${text.length}, parse_mode=${parseMode}")

    Http()
      .singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = url,
          entity = HttpEntity(ContentTypes.`application/json`, jsonPayload)
        )
      )
      .flatMap { res =>
        res.status match {
          case StatusCodes.OK =>
            res.entity.dataBytes.runReduce(_ ++ _)
          case _ =>
            val body = Await.result(
              res.entity.dataBytes.runReduce(_ ++ _),
              FiniteDuration(3000L, TimeUnit.MILLISECONDS)
            ).utf8String
            log.error(s"sendMessage failed for chat_id=${chatId}: ${res.status}: body=${body}")
            // Return error response instead of throwing
            Future.successful(ByteString(s"""{"ok":false,"description":"${body}","error_code":${res.status.intValue}}"""))
        }
      }
  }

  // Make HTTP request to Telegram Bot API getUpdates
  // Note: timeout parameter is in milliseconds, but Telegram API expects seconds
  def getUpdates(
    botToken: String,
    offset: Long = 0,
    timeout: Long = DEF_TIMEOUT,
    limit: Int = DEF_MAX,
    allowedUpdates: Seq[String] = Seq("message", "channel_post")
  )(implicit timeout_req: FiniteDuration): Future[ByteString] = {
    // Convert milliseconds to seconds for Telegram API (max 90 seconds)
    val timeoutSeconds = Math.min(timeout / 1000, 90)
    val allowedUpdatesJson = allowedUpdates.map(u => s""""$u"""").mkString("[", ",", "]")
    val url = s"${telegramUrlBase}${botToken}/getUpdates?offset=${offset}&timeout=${timeoutSeconds}&limit=${limit}&allowed_updates=${allowedUpdatesJson}"

    log.debug(s"[updates] offset=${offset}, timeout=${timeout}ms (${timeoutSeconds}s), limit=${limit}: url=${url}")

    Http()
      .singleRequest(HttpRequest(uri = url))
      .flatMap { res =>
        res.status match {
          case StatusCodes.OK =>
            res.entity.dataBytes.runReduce(_ ++ _)
          case _ =>
            val body = Await.result(
              res.entity.dataBytes.runReduce(_ ++ _),
              FiniteDuration(3000L, TimeUnit.MILLISECONDS)
            ).utf8String
            log.error(s"Failed to get updates: ${res.status}: body='${body}'")
            throw new Exception(s"Failed to get updates: ${res.status}")
        }
      }
  }

  // Parse Telegram API response and convert to TelegramMessage
  def parseUpdates(
    body: ByteString,
    channels: Set[String]
  ): Seq[TelegramMessage] = {
    try {
      val response = body.utf8String.parseJson.convertTo[TelegramGetUpdatesResponse]

      log.debug(s"[updates] rsp='${body.utf8String}'")

      if (!response.ok) {
        return Seq.empty
      }      

      val messages = response.result.flatMap { update =>
        // Handle both message and channel_post
        val apiMessage = update.message.orElse(update.channel_post).orElse(update.edited_channel_post)
        
        apiMessage.flatMap { msg =>
          val chatId = msg.chat.id.toString
          val chatType = msg.chat.`type`
          val chatTitle = msg.chat.title.getOrElse("(no title)")

          // Filter by chat IDs or chat names if specified
          val matchesFilter = channels.isEmpty ||
                             channels.contains(chatId) ||
                             channels.contains(chatTitle)

          if (!matchesFilter) {
            log.debug(s"[updates] Filtering: chat_id=${chatId}, title='${chatTitle}' (not in whitelist: ${channels})")
            None
          } else {
            val messageType = determineMessageType(msg)
            val preview = msg.text.map(t => if (t.length > 50) t.take(50) + "..." else t).getOrElse("(no text)")

            log.info(s"[updates] update_id=${update.update_id}, chat=${chatTitle}(${chatId}), type=${messageType}, preview='${preview}'")

            Some(TelegramMessage(
              update_id = update.update_id,
              message_id = msg.message_id,
              chat_id = msg.chat.id,
              chat_type = msg.chat.`type`,
              chat_title = msg.chat.title,
              from_id = msg.from.map(_.id),
              from_username = msg.from.flatMap(_.username),
              from_first_name = msg.from.map(_.first_name),
              date = msg.date,
              message_type = messageType,
              text = msg.text,
              caption = msg.caption,
              photo_file_ids = msg.photo.map(_.map(_.file_id)).getOrElse(Seq.empty),
              video_file_id = msg.video.map(_.file_id),
              document_file_id = msg.document.map(_.file_id),
              document_name = msg.document.flatMap(_.file_name),
              audio_file_id = msg.audio.map(_.file_id),
              voice_file_id = msg.voice.map(_.file_id),
              forward_from_chat_id = msg.forward_from_chat.map(_.id),
              reply_to_message_id = msg.reply_to_message.map(_.message_id)
            ))
          }
        }
      }

      // Only warn if we're accepting all channels but got no messages
      // If channels is non-empty, it's normal to receive updates for unsubscribed chats
      if (messages.isEmpty && response.result.nonEmpty) {
        if (channels.isEmpty) {
          log.warn(s"[updates] received ${response.result.size} updates but parsed 0 messages: '${response}'")
        } else {
          log.debug(s"[updates] received ${response.result.size} updates for unsubscribed chats, filtered to 0 messages")
        }
      } else {
        log.info(s"[updates] messages: ${messages.size} / ${response.result.size}")
      }
      messages
    } catch {
      case e: Exception =>
        log.error(s"Failed to parse updates: ${e.getMessage}", e)
        Seq.empty
    }
  }

  // Determine message type
  def determineMessageType(msg: TelegramApiMessage): String = {
    if (msg.text.isDefined) "text"
    else if (msg.photo.isDefined) "photo"
    else if (msg.video.isDefined) "video"
    else if (msg.document.isDefined) "document"
    else if (msg.audio.isDefined) "audio"
    else if (msg.voice.isDefined) "voice"
    else "unknown"
  }

  // Main source implementation with polling
  // Note: All timeout values are in milliseconds
  def source(
    botToken: String,
    channels: Set[String],
    freq: Long,
    timeout: Long,
    max: Int,
    allowedUpdates: Seq[String],
    bufferSize: Int,
    frameDelimiter: String,
    frameSize: Int
  ): Source[TelegramMessage, _] = {

    log.info(s"Telegram: channels=${channels}, freq=${freq}, timeout=${timeout}")
    
    // Add extra time for HTTP request timeout (API timeout + 5 seconds)
    implicit val timeout_req = FiniteDuration(timeout + 5000L, MILLISECONDS)

    // Detect chat types for numeric IDs
    val numericIds = channels.filter(isNumericId)
    if (numericIds.nonEmpty) {
      val chatTypes = detectChatTypes(botToken, numericIds)

      // Analyze detected types
      val channelCount = chatTypes.count(_._2._1 == "channel")
      val groupCount = chatTypes.count(t => t._2._1 == "group" || t._2._1 == "supergroup")
      val privateCount = chatTypes.count(_._2._1 == "private")

      log.info(s"Telegram: channels=${channelCount}, groups=${groupCount}, private=${privateCount}")

      // Recommend appropriate allowed_updates
      val hasChannels = channelCount > 0
      val hasGroups = groupCount > 0

      val recommendedUpdates = (hasChannels, hasGroups) match {
        case (true, true) => Seq("message", "channel_post")
        case (true, false) => Seq("channel_post")
        case (false, true) => Seq("message")
        case _ => allowedUpdates
      }

      if (allowedUpdates != recommendedUpdates && (hasChannels || hasGroups)) {
        log.debug(s"[updates] allowed_updates: ${recommendedUpdates.mkString(", ")} (currently: ${allowedUpdates.mkString(", ")})")
      }
      
    }

    // 1. Create ticking source and process updates with stateful offset tracking
    // Use a shared mutable variable to maintain offset across async operations
    var sharedOffset = 0L
    
    val s0 = Source.tick(
      FiniteDuration(250, TimeUnit.MILLISECONDS),
      FiniteDuration(freq, TimeUnit.MILLISECONDS),
      channels
    )
    .map(channels => {
      log.debug(s"Tick: channels=${channels}")
      channels
    })
    .mapAsync(1) { channels =>
      log.debug(s"State: offset=${sharedOffset}, channels=${channels}")
      // Make API request asynchronously using current offset
      getUpdates(botToken, sharedOffset, timeout, max, allowedUpdates)
        .recover {
          case e: Exception =>
            log.error(s"[updates] failed: ${e.getMessage}")
            ByteString.empty
        }
        .map(body => (channels, body))
    }
    .map { case (channels, body) =>
      if (body.isEmpty) {
        Seq.empty[TelegramMessage]
      } else {
        val messages = parseUpdates(body, channels)
        
        // Update shared offset based on messages
        if (messages.nonEmpty) {
          sharedOffset = messages.map(_.update_id).max + 1
          log.info(s"[updates] messages=${messages.size}, offset=${sharedOffset}")
        }
        
        messages
      }
    }
    .mapConcat(identity)

    // 2. Deduplicate by update_id
    .statefulMapConcat { () =>
      var seen = Set.empty[Long]
      (msg: TelegramMessage) => {
        if (!seen.contains(msg.update_id)) {
          seen = seen + msg.update_id
          // Limit buffer size
          if (seen.size > bufferSize) {
            val toRemove = seen.size - (bufferSize * 9 / 10)
            seen = seen.drop(toRemove)
            //log.debug(s"Buffer cleanup: removed ${toRemove} entries")
          }
          Seq(msg)
        } else {
          //log.debug(s"Duplicate update_id=${msg.update_id}, skipping")
          Seq.empty
        }
      }
    }

    s0
  }
}
