package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/*
Phone-based Telegram User API (TDLib/TDLight) for TelegramUserClient.
Only tel://phone is supported. Credentials from env (TELEGRAM_API_ID, TELEGRAM_API_HASH, TELEGRAM_PHONE)
or URI params (api_id, api_hash, phone).

  tel://phone                 - phone for auth (session: <phone>.session, no '+' in filename)
  tel://phone?session=path     - custom session path
  tel://?api_id=1&api_hash=...&phone=...  - all in params

Session file: default <phone>.session (phone without '+' prefix). Or session=path in URI.
*/
case class TelURI(uri: String) {
  val PREFIX = "tel://"

  val DEF_FREQ = 3000L
  val DEF_TIMEOUT = 30000L
  val DEF_BUFFER = 1000
  val DEF_MAX = 100
  val DEF_ALLOWED_UPDATES = Seq("message", "channel_post")

  private val (_freq: Long, _timeout: Long, _buffer: Int, _max: Int,
               _allowedUpdates: Seq[String], _ops: Map[String, String],
               _apiId: Option[Int], _apiHash: Option[String], _phone: Option[String], _sessionPath: Option[String]) = parse(uri)

  def freq: Long = _freq
  def timeout: Long = _timeout
  def buffer: Int = _buffer
  def max: Int = _max
  def allowedUpdates: Seq[String] = _allowedUpdates
  def ops: Map[String, String] = _ops

  def apiId: Option[Int] = _apiId
  def apiHash: Option[String] = _apiHash
  def phone: Option[String] = _phone
  def sessionPath: Option[String] = _sessionPath

  /** Phone normalized for session filename: digits only (no '+') */
  def phoneForSessionFile(phone: String): String = phone.replace("+", "").filter(_.isDigit)

  def parse(uri: String): (Long, Long, Int, Int, Seq[String], Map[String, String], Option[Int], Option[String], Option[String], Option[String]) = {
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
    val phoneFromOps = ops.get("phone").orElse(sys.env.get("TELEGRAM_PHONE"))
    val pathIsPhone = pathPart.nonEmpty && pathPart.matches("^\\+?[0-9]+$")
    val pathPhone = if (pathIsPhone) Some(pathPart) else None
    val phone = phoneFromOps.orElse(pathPhone)
    val sessionPath = ops.get("session").orElse(sys.env.get("TELEGRAM_SESSION")).orElse(
      phone.map(p => s"${phoneForSessionFile(p)}.session")
    )
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
      sessionPath
    )
  }
}
