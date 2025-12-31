package io.syspulse.skel.telegram

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._
import java.nio.file.{Path, Paths}
import java.util.concurrent.CompletableFuture

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.Logger

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
  private val log = Logger(s"${this}")

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
    log.info(s"Creating TDLight client with session: ${getSessionPath()}")

    // Create API token
    val apiToken = new APIToken(getApiId(), getApiHash())

    // Create TDLib settings
    val settings = TDLibSettings.create(apiToken)
    val sessionPath = Paths.get(getSessionPath())
    settings.setDatabaseDirectoryPath(sessionPath.resolve("data"))
    settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"))

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
  sessionPath: String = "./tdlight-session"
) extends TelegramUserClient {

  override def getApiId(): Int = apiId
  override def getApiHash(): String = apiHash
  override def getPhoneNumber(): String = phone
  override def getSessionPath(): String = sessionPath

  /**
   * Run the session: authenticate and listen for messages
   */
  def run(): Unit = {
    println("="*60)
    println("Telegram User API (TDLight)")
    println("="*60)
    println()
    println(s"API ID:    ${getApiId()}")
    println(s"API Hash:  ${getApiHash().take(8)}...")
    println(s"Phone:     ${getPhoneNumber()}")
    println(s"Session:   ${getSessionPath()}")
    println()
    println("="*60)
    println()

    // Latch to wait for authentication
    val authLatch = new java.util.concurrent.CountDownLatch(1)
    @volatile var authError: Option[Throwable] = None
    @volatile var isAuthenticated = false

    // Create client
    println("Step 1: Creating Telegram client...")
    createClient() match {
      case Failure(e) =>
        println(s"✗ Failed to create client: ${e.getMessage}")
        e.printStackTrace()
        sys.exit(1)

      case Success(client) =>
        println("✓ Successfully created client!")
        println()

        // Add authentication state handler
        client.addUpdateHandler(classOf[UpdateAuthorizationState], new GenericUpdateHandler[UpdateAuthorizationState] {
          override def onUpdate(update: UpdateAuthorizationState): Unit = {
            update.authorizationState match {
              case _: AuthorizationStateReady =>
                println()
                println("✓ Authentication successful!")
                isAuthenticated = true
                authLatch.countDown()

              case waitCode: AuthorizationStateWaitCode =>
                println()
                println("="*60)
                println("VERIFICATION CODE REQUIRED")
                println("="*60)
                println(s"Phone: ${waitCode.codeInfo.phoneNumber}")
                println("Check your Telegram app for the verification code")
                print("Enter code: ")

                try {
                  val code = scala.io.StdIn.readLine().trim
                  client.send(new CheckAuthenticationCode(code)).whenCompleteAsync((result, error) => {
                    if (error != null) {
                      println(s"✗ Error submitting code: ${error.getMessage}")
                      authError = Some(error)
                      authLatch.countDown()
                    }
                  })
                } catch {
                  case e: Exception =>
                    println(s"✗ Error reading code: ${e.getMessage}")
                    authError = Some(e)
                    authLatch.countDown()
                }

              case waitPassword: AuthorizationStateWaitPassword =>
                println()
                println("="*60)
                println("2FA PASSWORD REQUIRED")
                println("="*60)
                if (waitPassword.passwordHint != null && waitPassword.passwordHint.nonEmpty) {
                  println(s"Hint: ${waitPassword.passwordHint}")
                }
                print("Enter 2FA password: ")

                try {
                  val password = scala.io.StdIn.readLine().trim
                  client.send(new CheckAuthenticationPassword(password)).whenCompleteAsync((result, error) => {
                    if (error != null) {
                      println(s"✗ Error submitting password: ${error.getMessage}")
                      authError = Some(error)
                      authLatch.countDown()
                    }
                  })
                } catch {
                  case e: Exception =>
                    println(s"✗ Error reading password: ${e.getMessage}")
                    authError = Some(e)
                    authLatch.countDown()
                }

              case _: AuthorizationStateWaitPhoneNumber =>
                println("Waiting for phone number...")

              case _: AuthorizationStateWaitTdlibParameters =>
                println("Initializing TDLib...")

              case closed: AuthorizationStateClosed =>
                println("✗ Client closed")
                authError = Some(new Exception("Client closed during authentication"))
                authLatch.countDown()

              case _: AuthorizationStateClosing =>
                println("Client closing...")

              case _: AuthorizationStateLoggingOut =>
                println("Logging out...")

              case _ =>
                // Other states
            }
          }
        })

        try {
          // Wait for authentication to complete (max 5 minutes)
          println("Waiting for authentication...")
          val authenticated = authLatch.await(5, java.util.concurrent.TimeUnit.MINUTES)

          if (!authenticated) {
            println("✗ Authentication timeout after 5 minutes")
            client.close()
            Thread.sleep(1000)
            sys.exit(1)
          }

          authError match {
            case Some(error) =>
              println(s"✗ Authentication failed: ${error.getMessage}")
              error.printStackTrace()
              client.close()
              Thread.sleep(1000)
              sys.exit(1)

            case None if !isAuthenticated =>
              println("✗ Authentication failed for unknown reason")
              client.close()
              Thread.sleep(1000)
              sys.exit(1)

            case None =>
              // Authentication successful, start listening for messages
              println()
              println("="*60)
              println("✓ Ready!")
              println("="*60)
              println()
              println("Listening for new messages...")
              println("Press Ctrl+C to stop")
              println()

              // Add handler for new messages
              client.addUpdateHandler(classOf[UpdateNewMessage], new GenericUpdateHandler[UpdateNewMessage] {
                override def onUpdate(update: UpdateNewMessage): Unit = {
                  val msg = update.message
                  val chatId = msg.chatId

                  // Get chat info
                  client.send(new GetChat(chatId)).whenCompleteAsync((chat, error) => {
                    if (error != null) {
                      println(s"[ERROR] Failed to get chat info: ${error.getMessage}")
                    } else {
                      val chatTitle = chat.title
                      val chatType = getChatType(chat.`type`)

                      // Format timestamp
                      val timestamp = new java.util.Date(msg.date * 1000L)

                      // Get sender info
                      val senderId = msg.senderId match {
                        case user: TdApi.MessageSenderUser => s"User ${user.userId}"
                        case chat: TdApi.MessageSenderChat => s"Chat ${chat.chatId}"
                        case _ => "Unknown"
                      }

                      // Extract message content
                      val messageText = msg.content match {
                        case text: TdApi.MessageText => text.text.text
                        case photo: TdApi.MessagePhoto =>
                          val caption = if (photo.caption.text.nonEmpty) s" - ${photo.caption.text}" else ""
                          s"[Photo]$caption"
                        case video: TdApi.MessageVideo =>
                          val caption = if (video.caption.text.nonEmpty) s" - ${video.caption.text}" else ""
                          s"[Video]$caption"
                        case doc: TdApi.MessageDocument => s"[Document: ${doc.document.fileName}]"
                        case audio: TdApi.MessageAudio => "[Audio]"
                        case voice: TdApi.MessageVoiceNote => "[Voice message]"
                        case sticker: TdApi.MessageSticker => s"[Sticker: ${sticker.sticker.emoji}]"
                        case location: TdApi.MessageLocation => "[Location]"
                        case contact: TdApi.MessageContact => "[Contact]"
                        case poll: TdApi.MessagePoll => s"[Poll: ${poll.poll.question}]"
                        case _ => s"[${msg.content.getClass.getSimpleName}]"
                      }

                      // Display message
                      println(s"[$chatTitle] [$chatType] [${timestamp}] $senderId: $messageText")
                    }
                  })
                }
              })

              println(s"Session saved to: ${getSessionPath()}/")
              println("Monitoring all chats for new messages...")
              println()

              // Keep running indefinitely
              while (true) {
                Thread.sleep(1000)
              }
          }
        } catch {
          case e: InterruptedException =>
            println()
            println("Shutting down...")
            client.close()
            Thread.sleep(500)
            sys.exit(0)

          case e: Exception =>
            println(s"✗ Error: ${e.getMessage}")
            e.printStackTrace()
            client.close()
            Thread.sleep(500)
            sys.exit(1)
        }
    }
  }
}
