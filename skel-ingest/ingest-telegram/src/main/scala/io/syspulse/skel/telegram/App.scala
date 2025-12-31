package io.syspulse.skel.telegram

/**
 * Main application entry point for Telegram User API
 *
 * Usage:
 *   export TELEGRAM_API_ID="12345678"
 *   export TELEGRAM_API_HASH="0123456789abcdef0123456789abcdef"
 *   export TELEGRAM_PHONE="+1234567890"
 *
 *   ./run-telegram-sbt.sh
 *
 * On first run, you'll be prompted to enter the verification code from your Telegram app.
 * Session will be saved to ./tdlight-session/ and reused on subsequent runs.
 */
object App extends App {

  // Read configuration from environment variables
  val apiId = sys.env.get("TELEGRAM_API_ID") match {
    case Some(id) => id.toInt
    case None =>
      println("ERROR: TELEGRAM_API_ID not set")
      println("Get it from: https://my.telegram.org/apps")
      sys.exit(1)
  }

  val apiHash = sys.env.get("TELEGRAM_API_HASH") match {
    case Some(hash) => hash
    case None =>
      println("ERROR: TELEGRAM_API_HASH not set")
      println("Get it from: https://my.telegram.org/apps")
      sys.exit(1)
  }

  val phone = sys.env.get("TELEGRAM_PHONE") match {
    case Some(p) => p
    case None =>
      println("ERROR: TELEGRAM_PHONE not set (e.g., +1234567890)")
      sys.exit(1)
  }

  val sessionPath = sys.env.getOrElse("TELEGRAM_SESSION", "./tdlight-session")

  // Create and run session
  val session = new TelegramUserSession(apiId, apiHash, phone, sessionPath)
  session.run()
}
