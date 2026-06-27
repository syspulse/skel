package io.syspulse.skel.ai.provider

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import io.syspulse.skel.ai.{Ai, Chat}
import io.syspulse.skel.ai.ChatMessage
import io.syspulse.skel.ai.core.{OpenAiURI, ClaudeURI}

object AiProviderAsyncSpec {
  val testQuestion = "Reply with exactly: OK"
  val testTimeoutMs = 60000L
  val openAiModel = OpenAiURI.DEFAULT_MODEL
  val claudeModel = "claude-sonnet-4-5"

  def hasApiKey(env: String): Boolean =
    sys.env.get(env).exists(_.nonEmpty)
}

trait AiProviderAsyncTests { this: AnyWordSpec with Matchers =>

  implicit val ec: ExecutionContext = ExecutionContext.global

  def provider: AiProvider
  def providerLabel: String
  def apiKeyEnv: String
  def model: String
  def includeMessagesAsync: Boolean

  private val question = AiProviderAsyncSpec.testQuestion
  private val timeoutMs = AiProviderAsyncSpec.testTimeoutMs

  private def requireApiKey(): Unit =
    if (!AiProviderAsyncSpec.hasApiKey(apiKeyEnv))
      cancel(s"$apiKeyEnv is not set; skipping live $providerLabel API test")

  private def awaitAi(f: Future[Ai]): Ai =
    Await.result(f, timeoutMs.millis)

  private def awaitChat(f: Future[Chat]): Chat =
    Await.result(f, timeoutMs.millis)

  def runAiProviderAsyncTests(): Unit = {
    s"$providerLabel askAsync" should {
      "return a non-empty answer" in {
        requireApiKey()
        val ai = awaitAi(provider.askAsync(question, Some(model), timeout = timeoutMs, retry = 1))
        ai.answer shouldBe defined
        ai.answer.get should not be empty
      }
    }

    s"$providerLabel chatAsync" should {
      "append an assistant reply" in {
        requireApiKey()
        val chat0 = Chat(messages = Seq(ChatMessage("user", question)))
        val chat = awaitChat(provider.chatAsync(chat0, Some(model), timeout = timeoutMs, retry = 1))
        chat.messages.size should be > chat0.messages.size
        chat.messages.last.role shouldBe "assistant"
        chat.messages.last.content should not be empty
      }
    }

    s"$providerLabel promptAsync" should {
      "return a non-empty answer with conversation context" in {
        requireApiKey()
        val ai0 = Ai(question = question, model = Some(model))
        val ai = awaitAi(provider.promptAsync(ai0, timeout = timeoutMs, retry = 1))
        ai.answer shouldBe defined
        ai.answer.get should not be empty
      }
    }

    if (includeMessagesAsync) {
      s"$providerLabel messagesAsync" should {
        "return a non-empty answer via messages API" in {
          requireApiKey()
          val ai0 = Ai(question = question, model = Some(model))
          val ai = awaitAi(provider.messagesAsync(ai0, timeout = timeoutMs, retry = 1))
          ai.answer shouldBe defined
          ai.answer.get should not be empty
        }
      }
    }
  }
}

class OpenAiProviderAsyncSpec extends AnyWordSpec with Matchers with AiProviderAsyncTests {

  override val provider: AiProvider =
    AiProvider(s"openai://${AiProviderAsyncSpec.openAiModel}")

  override val providerLabel = "OpenAi"
  override val apiKeyEnv = OpenAiURI.ENV_KEY_NAME
  override val model = AiProviderAsyncSpec.openAiModel
  override val includeMessagesAsync = false

  "OpenAi AiProvider async" should {
    runAiProviderAsyncTests()
  }
}

class ClaudeProviderAsyncSpec extends AnyWordSpec with Matchers with AiProviderAsyncTests {

  override val provider: AiProvider =
    AiProvider(s"claude://${AiProviderAsyncSpec.claudeModel}")

  override val providerLabel = "Claude"
  override val apiKeyEnv = ClaudeURI.ENV_KEY_NAME
  override val model = AiProviderAsyncSpec.claudeModel
  override val includeMessagesAsync = true

  "Claude AiProvider async" should {
    runAiProviderAsyncTests()
  }
}
