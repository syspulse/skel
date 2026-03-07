package io.syspulse.skel.telegram

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Sink

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config._
import io.syspulse.skel.uri.{TelURI, TelegramURI}

/**
 * Telegram app. Supports both URIs:
 *   tel://phone       - User API (TelegramUserClient), credentials from env or URI params
 *   telegram://...    - Bot API (getUpdates), use with FromTelegram in pipelines or run here
 */
case class Config(
  feed: String = "tel://",
  cmd: String = "run",
  params: Seq[String] = Seq(),
)

object App {
  private val log = Logger(getClass)

  def main(args: Array[String]): Unit = {
    run(args).get
  }

  /** Runs the app. Returns Failure on error (no sys.exit). */
  def run(args: Array[String]): Try[Unit] = {
    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv,
      new ConfigurationArgs(args, "ingest-telegram", "",
        ArgString('f', "feed", s"URI: tel://phone (User API) or telegram://bot@channels (Bot API) (def=${d.feed})"),
        ArgCmd("run", "Run session (User API or Bot API depending on URI)"),
        ArgParam("<params>", "Optional params"),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      feed = c.getString("feed").getOrElse(d.feed),
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    log.info(s"Config: $config")

    config.cmd match {
      case "run" =>
        val uri = config.feed
        if (uri.startsWith("tel://")) runUserApi(uri)
        else if (uri.startsWith("telegram://")) runBotApi(uri)
        else Failure(new IllegalArgumentException(s"Feed must be tel:// (User API) or telegram:// (Bot API), got: $uri"))
      case _ =>
        Failure(new IllegalArgumentException(s"Unknown command: ${config.cmd}"))
    }
  }

  private def runUserApi(uri: String): Try[Unit] = {
    val telUri = TelURI(uri)
    val apiId = telUri.apiId.getOrElse {
      log.error("TELEGRAM_API_ID not set (env or api_id in URI). Get from https://my.telegram.org/apps")
      return Failure(new IllegalStateException("TELEGRAM_API_ID required"))
    }
    val apiHash = telUri.apiHash.getOrElse {
      log.error("TELEGRAM_API_HASH not set (env or api_hash in URI). Get from https://my.telegram.org/apps")
      return Failure(new IllegalStateException("TELEGRAM_API_HASH required"))
    }
    val phone = telUri.phone.getOrElse {
      log.error("TELEGRAM_PHONE not set (env, URI path, or phone= in URI)")
      return Failure(new IllegalStateException("TELEGRAM_PHONE required"))
    }
    val sessionPath = telUri.sessionPath
    
    val session = new TelegramUserSession(
      apiId, apiHash, phone, sessionPath,
      authTimeoutSec = telUri.authTimeoutSec,
      loopIntervalMs = telUri.loopIntervalMs
    )
    session.run()
  }

  private def runBotApi(uri: String): Try[Unit] = Try {
    val telegramUri = TelegramURI(uri)
    if (telegramUri.botToken.isEmpty) {
      log.error("Bot token required. Set TELEGRAM_BOT_TOKEN or use telegram://token@channels")
      throw new IllegalStateException("TELEGRAM_BOT_TOKEN required")
    }
    if (telegramUri.channels.isEmpty) {
      log.error("At least one channel/chat required in telegram:// URI")
      throw new IllegalStateException("telegram:// URI must include channel(s)")
    }
    log.info(s"Bot API: channels=${telegramUri.channels.mkString(",")}")
    val from = new FromTelegram(uri)
    implicit val mat: Materializer = SystemMaterializer(from.as).materializer
    val done = from.source().runWith(Sink.foreach(msg => log.info(msg.utf8String)))
    Await.result(done, Duration.Inf)
  }
}
