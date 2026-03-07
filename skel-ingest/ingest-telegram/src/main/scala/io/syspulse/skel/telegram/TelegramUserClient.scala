package io.syspulse.skel.telegram

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import java.nio.file.{Files, Path, Paths}

import os._
import java.util.concurrent.CompletableFuture

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.Logger

import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client._
import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi._

/**
 * Telegram User API client using TDLight Java (TDLib-based)
 * Allows connecting as a regular user instead of a bot
 *
 * Usage:
 * 1. Obtain api_id and api_hash from https://my.telegram.org
 * 2. First run requires phone number and verification code
 * 3. Session is saved and reused for subsequent runs
 */
trait TelegramUserClient {
  protected val log = Logger(s"${this}")

  implicit val as: ActorSystem = ActorSystem("ActorSystem-TelegramUserClient")
  implicit val ec: ExecutionContext = as.dispatcher

  // Configuration
  def getApiId(): Int
  def getApiHash(): String
  def getPhoneNumber(): String
  def getSessionPath(): String = "./tdlight-session"

  // Callback for interactive code verification
  def onCodeRequired(): String = {
    log.warn(s"Code verification required! Check your Telegram app for the code.")
    sys.env.getOrElse("TELEGRAM_CODE", {
      throw new IllegalStateException("Verification code required. Set TELEGRAM_CODE environment variable.")
    })
  }

  // Callback for 2FA password if enabled
  def onPasswordRequired(): String = {
    log.warn(s"2FA password required!")
    sys.env.getOrElse("TELEGRAM_PASSWORD", {
      throw new IllegalStateException("2FA password required. Set TELEGRAM_PASSWORD environment variable.")
    })
  }

  /**
   * Create and authenticate Telegram client
   */
  def createClient(): Try[SimpleTelegramClient] = Try {
    log.info(s"Creating TDLight client with session path: ${getSessionPath()}")

    // Route TDLib native logs through SLF4J (so logback level applies). Verbosity: 0=Fatal, 1=Error, 2=Warning, 3=Info, 4=Debug, 5=Verbose.
    Init.init()
    val verbosity = sys.env.get("TDLIGHT_LOG_VERBOSITY").fold(2)(_.toInt)
    Log.setLogMessageHandler(verbosity, new Slf4JLogMessageHandler())

    // Create API token
    val apiToken = new APIToken(getApiId(), getApiHash())

    // Create TDLib settings. Resolve session path to absolute so the same directory is used every run. Default: relative paths are resolved against current directory.
    val settings = TDLibSettings.create(apiToken)
    val sessionPathRaw = Paths.get(getSessionPath())
    val sessionPathAbs = sessionPathRaw.toAbsolutePath.normalize
    Files.createDirectories(sessionPathAbs)
    log.info(s"Session directory (absolute): $sessionPathAbs")
    settings.setDatabaseDirectoryPath(sessionPathAbs.resolve("data"))
    settings.setDownloadedFilesDirectoryPath(sessionPathAbs.resolve("downloads"))

    // Create client factory (singleton)
    val clientFactory = new SimpleTelegramClientFactory()

    // Create custom authentication supplier that uses environment variables
    val authData = AuthenticationSupplier.user(getPhoneNumber())

    // Build client
    val clientBuilder = clientFactory.builder(settings)
    val client = clientBuilder.build(authData)

    log.info(s"TDLight client created successfully")
    client
  }

  /**
   * Get recent chats (dialogs)
   */
  def getDialogs(client: SimpleTelegramClient, limit: Int = 100): Future[Seq[Chat]] = {
    val promise = Promise[Seq[Chat]]()

    try {
      // Get main chat list (up to limit chats)
      val request = new GetChats(new ChatListMain(), limit)

      client.send(request).whenCompleteAsync((chatsResult, error) => {
        if (error != null) {
          log.error(s"Failed to get chats: ${error.getMessage}", error)
          promise.failure(error)
        } else {
          val chatIds = chatsResult.chatIds
          log.info(s"Retrieved ${chatIds.length} chat IDs")

          // Fetch full chat objects
          val chatFutures = chatIds.map { chatId =>
            val chatPromise = Promise[Chat]()

            client.send(new GetChat(chatId)).whenCompleteAsync((chat, chatError) => {
              if (chatError != null) {
                log.warn(s"Failed to get chat $chatId: ${chatError.getMessage}")
                chatPromise.failure(chatError)
              } else {
                chatPromise.success(chat)
              }
            })

            chatPromise.future.recover { case _ => null }
          }

          // Wait for all chats
          Future.sequence(chatFutures.toSeq).map(_.filter(_ != null)).foreach { fullChats =>
            promise.success(fullChats)
          }
        }
      })
    } catch {
      case e: Exception =>
        log.error(s"Failed to get dialogs: ${e.getMessage}", e)
        promise.failure(e)
    }

    promise.future
  }

  /**
   * Get messages from a specific chat
   */
  def getMessages(
    client: SimpleTelegramClient,
    chatId: Long,
    limit: Int = 100,
    fromMessageId: Long = 0
  ): Future[Seq[Message]] = {
    val promise = Promise[Seq[Message]]()

    try {
      val request = new GetChatHistory(
        chatId,
        fromMessageId,
        0,  // offset
        limit,
        false  // only local
      )

      client.send(request).whenCompleteAsync((messagesResult, error) => {
        if (error != null) {
          log.error(s"Failed to get messages: ${error.getMessage}", error)
          promise.failure(error)
        } else {
          val messages = messagesResult.messages
          log.debug(s"Retrieved ${messages.length} messages from chat $chatId")
          promise.success(messages.toSeq)
        }
      })
    } catch {
      case e: Exception =>
        log.error(s"Failed to get messages: ${e.getMessage}", e)
        promise.failure(e)
    }

    promise.future
  }

  /**
   * Convert TDLib message to TelegramMessage model
   */
  def convertMessage(
    msg: Message,
    chatId: Long,
    chatType: String,
    chatTitle: Option[String]
  ): Option[TelegramMessage] = {
    try {
      val senderId = msg.senderId match {
        case user: MessageSenderUser => Some(user.userId)
        case _ => None
      }

      val messageType = determineMessageType(msg)

      Some(TelegramMessage(
        update_id = msg.id,
        message_id = msg.id,
        chat_id = chatId,
        chat_type = chatType,
        chat_title = chatTitle,
        from_id = senderId,
        from_username = None, // Would need separate user query
        from_first_name = None, // Would need separate user query
        date = msg.date.toLong,
        message_type = messageType,
        text = extractText(msg.content),
        caption = extractCaption(msg.content),
        photo_file_ids = extractPhotoIds(msg.content),
        video_file_id = extractVideoId(msg.content),
        document_file_id = extractDocumentId(msg.content),
        document_name = extractDocumentName(msg.content),
        audio_file_id = None,
        voice_file_id = None,
        forward_from_chat_id = None,
        reply_to_message_id = if (msg.replyTo != null) extractReplyToMessageId(msg.replyTo) else None
      ))
    } catch {
      case e: Exception =>
        log.warn(s"Failed to convert message ${msg.id}: ${e.getMessage}")
        None
    }
  }

  /**
   * Determine message type from TDLib message content
   */
  private def determineMessageType(message: Message): String = {
    message.content match {
      case _: MessageText => "text"
      case _: MessagePhoto => "photo"
      case _: MessageVideo => "video"
      case _: MessageDocument => "document"
      case _: MessageAudio => "audio"
      case _: MessageVoiceNote => "voice"
      case _: MessageLocation => "location"
      case _: MessageContact => "contact"
      case _ => "unknown"
    }
  }

  private def extractText(content: MessageContent): Option[String] = content match {
    case text: MessageText => Some(text.text.text).filter(_.nonEmpty)
    case _ => None
  }

  private def extractCaption(content: MessageContent): Option[String] = content match {
    case photo: MessagePhoto => Some(photo.caption.text).filter(_.nonEmpty)
    case video: MessageVideo => Some(video.caption.text).filter(_.nonEmpty)
    case doc: MessageDocument => Some(doc.caption.text).filter(_.nonEmpty)
    case _ => None
  }

  private def extractPhotoIds(content: MessageContent): Seq[String] = content match {
    case photo: MessagePhoto => photo.photo.sizes.map(_.photo.id.toString).toSeq
    case _ => Seq.empty
  }

  private def extractVideoId(content: MessageContent): Option[String] = content match {
    case video: MessageVideo => Some(video.video.video.id.toString)
    case _ => None
  }

  private def extractDocumentId(content: MessageContent): Option[String] = content match {
    case doc: MessageDocument => Some(doc.document.document.id.toString)
    case _ => None
  }

  private def extractDocumentName(content: MessageContent): Option[String] = content match {
    case doc: MessageDocument => Some(doc.document.fileName).filter(_.nonEmpty)
    case _ => None
  }

  private def extractReplyToMessageId(replyTo: MessageReplyTo): Option[Long] = replyTo match {
    case reply: MessageReplyToMessage => Some(reply.messageId)
    case _ => None
  }

  /**
   * Get chat type as string
   */
  def getChatType(chatType: ChatType): String = chatType match {
    case _: ChatTypePrivate => "private"
    case _: ChatTypeBasicGroup => "group"
    case _: ChatTypeSupergroup => "supergroup"
    case _: ChatTypeSecret => "secret"
    case _ => "unknown"
  }

  /**
   * Stream messages from specified chats
   */
  def source(
    channels: Set[String],
    freq: Long = 5000L,
    max: Int = 100
  ): Source[TelegramMessage, _] = {

    log.info(s"TelegramUserClient: channels=${channels}, freq=${freq}ms, max=${max}")

    // Create client once
    val clientTry = createClient()

    clientTry match {
      case Failure(e) =>
        log.error(s"Failed to create Telegram client: ${e.getMessage}", e)
        Source.empty

      case Success(client) =>
        // Store update handler for new messages
        val messageQueue = new java.util.concurrent.ConcurrentLinkedQueue[TelegramMessage]()

        // Track last message IDs per chat
        val lastMessageIds = new scala.collection.concurrent.TrieMap[Long, Long]()

        // Add update handler for new messages
        client.addUpdateHandler(classOf[UpdateNewMessage], new GenericUpdateHandler[UpdateNewMessage] {
          override def onUpdate(update: UpdateNewMessage): Unit = {
            val msg = update.message
            val chatId = msg.chatId

            // Get chat info
            client.send(new GetChat(chatId)).whenCompleteAsync((chat, error) => {
              if (error != null) {
                log.warn(s"Failed to get chat info for $chatId: ${error.getMessage}")
              } else {
                val chatType = getChatType(chat.`type`)
                val chatTitle = chat.title

                convertMessage(msg, chatId, chatType, Some(chatTitle)).foreach { telegramMsg =>
                  log.info(s"[${chatTitle}] New message: ${msg.id}")
                  messageQueue.offer(telegramMsg)
                }
              }
            })
          }
        })

        log.info("Starting message stream (listening for updates)...")

        // Create polling source that checks the queue
        Source.tick(
          500.milliseconds,
          100.milliseconds,  // Check queue frequently
          ()
        )
        .mapConcat { _ =>
          val messages = new scala.collection.mutable.ArrayBuffer[TelegramMessage]()
          var msg = messageQueue.poll()
          while (msg != null) {
            messages += msg
            msg = messageQueue.poll()
          }
          messages.toSeq
        }
    }
  }
}

/**
 * Interactive Telegram User Session
 * Handles authentication and real-time message monitoring
 */
class TelegramUserSession(
  apiId: Int,
  apiHash: String,
  phone: String,
  sessionPath: String = "./tdlight-session",
  authTimeoutSec: Long = 300L,
  loopIntervalMs: Long = 1000L
) extends TelegramUserClient {

  override def getApiId(): Int = apiId
  override def getApiHash(): String = apiHash
  override def getPhoneNumber(): String = phone
  override def getSessionPath(): String = sessionPath

  /** Poll interval (ms) for reading code/password from session path files. */
  private val CODE_POLL_INTERVAL_MS = 5000L

  /**
   * Poll file under session path until it contains a non-empty first line.
   * Each attempt: read with os.read. On exception or empty: log (exception is logged clearly), sleep, try again.
   */
  private def pollFileForLine(fileName: String): String = {
    val file = os.Path(getSessionPath(), os.pwd) / fileName
    var result = ""
    var attempt = 0
    while (result.isBlank) {
      attempt += 1
      result = Try(os.read(file)) match {        
        case Success(r) if(r.isBlank) =>
          log.info(s"$file [${attempt}]: empty. Retrying in ${CODE_POLL_INTERVAL_MS}ms")
          Thread.sleep(CODE_POLL_INTERVAL_MS)
          ""
        case Success(r) => r.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse("")

        case Failure(e) =>
          log.warn(s"$file [${attempt}]: failed: ${e.getClass.getSimpleName}: ${e.getMessage}. Retrying in ${CODE_POLL_INTERVAL_MS}ms")
          Thread.sleep(CODE_POLL_INTERVAL_MS)
          ""
      }
    }
    result
  }

  /**
   * Run the session: authenticate and listen for messages.
   * Returns Success(()) when shut down (e.g. Ctrl+C), Failure on error.
   * Uses authTimeoutSec for auth latch wait and loopIntervalMs for the idle loop sleep.
   */
  def run(): Try[Unit] = {
    val authLatch = new java.util.concurrent.CountDownLatch(1)
    @volatile var authError: Option[Throwable] = None
    @volatile var isAuthenticated = false

    createClient() match {
      case Failure(e) =>
        log.error(s"Failed to create client: ${e.getMessage}", e)
        Failure(e)

      case Success(client) =>
        log.info("Telegram client created successfully")

        client.addUpdateHandler(classOf[UpdateAuthorizationState], new GenericUpdateHandler[UpdateAuthorizationState] {
          override def onUpdate(update: UpdateAuthorizationState): Unit = {
            update.authorizationState match {
              case _: AuthorizationStateReady =>
                log.info("Authentication successful")
                isAuthenticated = true
                authLatch.countDown()

              case waitCode: AuthorizationStateWaitCode =>
                log.warn(s"Verification code required for phone: ${waitCode.codeInfo.phoneNumber}. Write code to ${Paths.get(getSessionPath()).resolve("code")}")
                try {
                  val code = pollFileForLine("code")
                  client.send(new CheckAuthenticationCode(code)).whenCompleteAsync((_, error) => {
                    if (error != null) {
                      log.error(s"Error submitting code: ${error.getMessage}", error)
                      authError = Some(error)
                      authLatch.countDown()
                    }
                  })
                } catch {
                  case e: Exception =>
                    log.error(s"Error reading code: ${e.getMessage}", e)
                    authError = Some(e)
                    authLatch.countDown()
                }

              case waitPassword: AuthorizationStateWaitPassword =>
                log.warn("2FA password required" + (if (waitPassword.passwordHint != null && waitPassword.passwordHint.nonEmpty) s" (hint: ${waitPassword.passwordHint})" else "") + s". Write password to ${Paths.get(getSessionPath()).resolve("password")}")
                try {
                  val password = pollFileForLine("password")
                  client.send(new CheckAuthenticationPassword(password)).whenCompleteAsync((_, error) => {
                    if (error != null) {
                      log.error(s"Error submitting password: ${error.getMessage}", error)
                      authError = Some(error)
                      authLatch.countDown()
                    }
                  })
                } catch {
                  case e: Exception =>
                    log.error(s"Error reading password: ${e.getMessage}", e)
                    authError = Some(e)
                    authLatch.countDown()
                }

              case _: AuthorizationStateWaitPhoneNumber =>
                log.info("Waiting for phone number...")

              case _: AuthorizationStateWaitTdlibParameters =>
                log.info("Initializing TDLib...")

              case _: AuthorizationStateClosed =>
                log.warn("Client closed during authentication")
                authError = Some(new Exception("Client closed during authentication"))
                authLatch.countDown()

              case _: AuthorizationStateClosing =>
                log.info("Client closing...")

              case _: AuthorizationStateLoggingOut =>
                log.info("Logging out...")

              case _ =>
            }
          }
        })

        val result = try {
          log.info(s"Waiting for authentication (max ${authTimeoutSec}s)...")
          val authenticated = authLatch.await(authTimeoutSec, java.util.concurrent.TimeUnit.SECONDS)

          if (!authenticated) {
            log.error(s"Authentication timeout after ${authTimeoutSec}s")
            client.close()            
            Failure(new Exception(s"Authentication timeout: ${authTimeoutSec}s"))
          } else authError match {
            case Some(error) =>
              log.error(s"Authentication failed: ${error.getMessage}", error)
              client.close()              
              Failure(error)

            case None if !isAuthenticated =>
              log.error("Authentication failed for unknown reason")
              client.close()              
              Failure(new Exception("Authentication failed"))

            case None =>
              log.info(s"Session: ${getSessionPath()}")

              client.addUpdateHandler(classOf[UpdateNewMessage], new GenericUpdateHandler[UpdateNewMessage] {
                override def onUpdate(update: UpdateNewMessage): Unit = {
                  val msg = update.message
                  val chatId = msg.chatId
                  client.send(new GetChat(chatId)).whenCompleteAsync((chat, error) => {
                    if (error != null) {
                      log.warn(s"Failed to get chat info for $chatId: ${error.getMessage}")
                    } else {
                      val chatTitle = chat.title
                      val chatType = getChatType(chat.`type`)
                      val timestamp = new java.util.Date(msg.date * 1000L)
                      val senderId = msg.senderId match {
                        case user: TdApi.MessageSenderUser => s"User ${user.userId}"
                        case c: TdApi.MessageSenderChat => s"Chat ${c.chatId}"
                        case _ => "Unknown"
                      }
                      val messageText = msg.content match {
                        case text: TdApi.MessageText => text.text.text
                        case photo: TdApi.MessagePhoto =>
                          val caption = if (photo.caption.text.nonEmpty) s" - ${photo.caption.text}" else ""
                          s"[Photo]$caption"
                        case video: TdApi.MessageVideo =>
                          val caption = if (video.caption.text.nonEmpty) s" - ${video.caption.text}" else ""
                          s"[Video]$caption"
                        case doc: TdApi.MessageDocument => s"[Document: ${doc.document.fileName}]"
                        case _: TdApi.MessageAudio => "[Audio]"
                        case _: TdApi.MessageVoiceNote => "[Voice message]"
                        case sticker: TdApi.MessageSticker => s"[Sticker: ${sticker.sticker.emoji}]"
                        case _: TdApi.MessageLocation => "[Location]"
                        case _: TdApi.MessageContact => "[Contact]"
                        case poll: TdApi.MessagePoll => s"[Poll: ${poll.poll.question}]"
                        case _ => s"[${msg.content.getClass.getSimpleName}]"
                      }
                      log.info(s"[$chatTitle] [$chatType] [${timestamp}] $senderId: $messageText")
                    }
                  })
                }
              })

              try {
                while (true) Thread.sleep(loopIntervalMs)
              } catch {
                case e: InterruptedException =>
                  log.warn(s"Shutting down: ${client}: ${e.getMessage}")
                  client.close()                  
              }
              Success(())
          }
        } catch {
          case e1: InterruptedException =>
            log.warn(s"Shutting down: ${client}: ${e1.getMessage}")
            client.close()            
            Success(())

          case e: Exception =>
            log.error(s"Failed to run session: ${e.getMessage}", e)
            client.close()            
            Failure(e)
        }
        result
    }
  }
}
