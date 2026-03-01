package io.syspulse.skel.telegram

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Sink

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

  def main(args: Array[String]): Unit = {
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

    Console.err.println(s"Config: $config")

    config.cmd match {
      case "run" =>
        val uri = config.feed
        if (uri.startsWith("tel://")) {
          runUserApi(uri)
        } else if (uri.startsWith("telegram://")) {
          runBotApi(uri)
        } else {
          Console.err.println(s"ERROR: Feed must be tel:// (User API) or telegram:// (Bot API), got: $uri")
          sys.exit(1)
        }
      case _ =>
        Console.err.println(s"Unknown command: ${config.cmd}")
        sys.exit(1)
    }
  }

  private def runUserApi(uri: String): Unit = {
    val telUri = TelURI(uri)
    val apiId = telUri.apiId.getOrElse {
      Console.err.println("ERROR: TELEGRAM_API_ID not set (env or api_id in URI). Get from https://my.telegram.org/apps")
      sys.exit(1)
    }
    val apiHash = telUri.apiHash.getOrElse {
      Console.err.println("ERROR: TELEGRAM_API_HASH not set (env or api_hash in URI). Get from https://my.telegram.org/apps")
      sys.exit(1)
    }
    val phone = telUri.phone.getOrElse {
      Console.err.println("ERROR: TELEGRAM_PHONE not set (env, URI path, or phone= in URI)")
      sys.exit(1)
    }
    val sessionPath = telUri.sessionPath.getOrElse {
      s"${telUri.phoneForSessionFile(phone)}.session"
    }
    val session = new TelegramUserSession(apiId, apiHash, phone, sessionPath)
    session.run()
  }

  private def runBotApi(uri: String): Unit = {
    val telegramUri = TelegramURI(uri)
    if (telegramUri.botToken.isEmpty) {
      Console.err.println("ERROR: Bot token required. Set TELEGRAM_BOT_TOKEN or use telegram://token@channels")
      sys.exit(1)
    }
    if (telegramUri.channels.isEmpty) {
      Console.err.println("ERROR: At least one channel/chat required in telegram:// URI")
      sys.exit(1)
    }
    Console.err.println(s"Bot API: channels=${telegramUri.channels.mkString(",")}")
    val from = new FromTelegram(uri)
    implicit val mat: Materializer = SystemMaterializer(from.as).materializer
    val done = from.source().runWith(Sink.foreach(msg => println(msg.utf8String)))
    Await.result(done, Duration.Inf)
  }
}
