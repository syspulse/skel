package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/*
Phone-based Telegram User API (TDLib/TDLight) for TelegramUserClient.
Phone from path or env only (no phone query param). Path overrides env.

  tel://phone       - phone in path (session: <phone>.session)
  tel://            - phone from TELEGRAM_PHONE env

  Path always overrides: tel://123 has phone 123 even if TELEGRAM_PHONE is set.
  Other credentials: TELEGRAM_API_ID, TELEGRAM_API_HASH (or api_id, api_hash in URI).

Options (with defaults): auth_timeout_sec (300), loop_interval_ms (1000), session=path.
*/
case class TelURI(uri: String) {
  val PREFIX = "tel://"

  val DEF_FREQ = 3000L
  val DEF_TIMEOUT = 30000L
  val DEF_BUFFER = 1000
  val DEF_MAX = 100
  val DEF_ALLOWED_UPDATES = Seq("message", "channel_post")
  /** Default auth wait timeout in seconds (5 minutes). */
  val DEF_AUTH_TIMEOUT_SEC = 300L
  /** Default main loop sleep interval in milliseconds. */
  val DEF_LOOP_INTERVAL_MS = 1000L

  val DEF_SESSION = "session"
  val DEF_SESSION_PATH = s"tdlight.${DEF_SESSION}"

  private val (_freq: Long, _timeout: Long, _buffer: Int, _max: Int,
               _allowedUpdates: Seq[String], _ops: Map[String, String],
               _apiId: Option[Int], _apiHash: Option[String], _phone: Option[String], _sessionPath: String,
               _authTimeoutSec: Long, _loopIntervalMs: Long) = parse(uri)

  def freq: Long = _freq
  def timeout: Long = _timeout
  def buffer: Int = _buffer
  def max: Int = _max
  def allowedUpdates: Seq[String] = _allowedUpdates
  def ops: Map[String, String] = _ops

  def apiId: Option[Int] = _apiId
  def apiHash: Option[String] = _apiHash
  def phone: Option[String] = _phone
  def sessionPath: String = _sessionPath
  def authTimeoutSec: Long = _authTimeoutSec
  def loopIntervalMs: Long = _loopIntervalMs

  /** Phone normalized for session filename: digits only (no '+') */
  def phoneForSessionFile(phone: String): String = phone.replace("+", "").filter(_.isDigit)

  def parse(uri: String): (Long, Long, Int, Int, Seq[String], Map[String, String], Option[Int], Option[String], Option[String], String, Long, Long) = {
    
    val (url: String, ops: Map[String, String]) = uri.split("[\\?&]").toList match {
      case url :: Nil => (url, Map())
      case url :: rest =>
        val vars = rest.flatMap(_.split("=").toList match {
          case k :: v :: Nil => Some(k -> Util.replaceEnvVar(v))
          case _ => None
        }).toMap
        (url, vars)
      case _ =>
        ("", Map())
    }

    val allowedUpdates = ops.get("allowed_updates") match {
      case Some(updates) => updates.split(",").toSeq
      case None => DEF_ALLOWED_UPDATES
    }

    val pathPart = url.stripPrefix(PREFIX).trim
    val apiId = ops.get("api_id").orElse(sys.env.get("TELEGRAM_API_ID")).map(_.toInt)
    val apiHash = ops.get("api_hash").orElse(sys.env.get("TELEGRAM_API_HASH"))
    val pathIsPhone = pathPart.nonEmpty && pathPart.matches("^\\+?[0-9]+$")
    val pathPhone = if (pathIsPhone) Some(pathPart) else None
    val phone = pathPhone.orElse(sys.env.get("TELEGRAM_PHONE"))
    
    val sessionPath = ops.get("session")      
      //.orElse(sys.env.get("TELEGRAM_SESSION"))      
    .orElse(phone.map(p => s"${phoneForSessionFile(p)}.${DEF_SESSION}"))
    .getOrElse(DEF_SESSION_PATH)    

    val authTimeoutSec = ops.get("auth_timeout_sec").map(_.toLong).getOrElse(DEF_AUTH_TIMEOUT_SEC)
    val loopIntervalMs = ops.get("loop_interval_ms").map(_.toLong).getOrElse(DEF_LOOP_INTERVAL_MS)
    (
      ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
      ops.get("timeout").map(_.toLong).getOrElse(DEF_TIMEOUT),
      ops.get("buffer").map(_.toInt).getOrElse(DEF_BUFFER),
      ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
      allowedUpdates,
      ops,
      apiId,
      apiHash,
      phone,
      sessionPath,
      authTimeoutSec,
      loopIntervalMs
    )
  }
}
