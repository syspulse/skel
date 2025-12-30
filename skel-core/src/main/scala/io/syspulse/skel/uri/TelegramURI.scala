package io.syspulse.skel.uri

import io.syspulse.skel.util.Util
import scala.util.Try

/*
telegram://bot_token@channel_id1,group_id2
telegram://bot_token@channel_id1,group_id2?freq=30000&timeout=30000
telegram://bot_token@chat_id1,chat_id2?freq=15000&timeout=20000&allowed_updates=message,channel_post&buffer=1000&max=100
telegram://channel_id1,group_id2  (bot_token from TELEGRAM_BOT_TOKEN env var)
Note: timeout is in milliseconds (Telegram API accepts seconds, conversion happens in client)

channel_id and group_id can be names or IDs (e.g. telegram://-1003611976348,-5111202365)
*/
case class TelegramURI(uri:String) {
  val PREFIX = "telegram://"

  val DEF_FREQ = 3000L     // 30 seconds in ms
  val DEF_TIMEOUT = 30000L  // 30 seconds in ms (Telegram default)
  val DEF_BUFFER = 1000
  val DEF_MAX = 100
  val DEF_ALLOWED_UPDATES = Seq("message", "channel_post")

  private val (_botToken:String, _channels:Seq[String], _freq:Long, _timeout:Long,
               _buffer:Int, _max:Int, _allowedUpdates:Seq[String], _ops:Map[String,String]) = parse(uri)

  def botToken:String = _botToken
  def channels:Seq[String] = _channels  // it is both channel and group
  def freq:Long = _freq
  def timeout:Long = _timeout  // in milliseconds
  def buffer:Int = _buffer
  def max:Int = _max
  def allowedUpdates:Seq[String] = _allowedUpdates
  def ops:Map[String,String] = _ops

  def parse(uri:String):(String,Seq[String],Long,Long,Int,Int,Seq[String],Map[String,String]) = {
    // resolve options
    val (url:String,ops:Map[String,String]) = uri.split("[\\?&]").toList match {
      case url :: Nil => (url,Map())
      case url :: ops =>
        val vars = ops.flatMap(_.split("=").toList match {
          case k :: v :: Nil => Some(k -> v)
          case _ => None
        }).toMap
        (url,vars)
      case _ =>
        ("",Map())
    }

    // Parse allowed_updates parameter
    val allowedUpdates = ops.get("allowed_updates") match {
      case Some(updates) => updates.split(",").toSeq
      case None => DEF_ALLOWED_UPDATES
    }

    url.stripPrefix(PREFIX).split("[@]").toList match {
      // telegram://bot_token@chat_id1,chat_id2
      case botToken :: channels :: Nil =>
        ( Util.replaceEnvVar(botToken),
          channels.split(",").toSeq,
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
          ops.get("timeout").map(_.toLong).getOrElse(DEF_TIMEOUT),
          ops.get("buffer").map(_.toInt).getOrElse(DEF_BUFFER),
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          allowedUpdates,
          ops
        )

      // telegram://@chat_id1,chat_id2 (bot token from env)
      case channels :: Nil if(!channels.isEmpty()) =>
        ( sys.env.get("TELEGRAM_BOT_TOKEN").getOrElse(""),
          channels.split(",").toSeq,
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
          ops.get("timeout").map(_.toLong).getOrElse(DEF_TIMEOUT),
          ops.get("buffer").map(_.toInt).getOrElse(DEF_BUFFER),
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          allowedUpdates,
          ops
        )

      case _ =>
        ( sys.env.get("TELEGRAM_BOT_TOKEN").getOrElse(""),
          Seq.empty,
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
          ops.get("timeout").map(_.toLong).getOrElse(DEF_TIMEOUT),
          ops.get("buffer").map(_.toInt).getOrElse(DEF_BUFFER),
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          allowedUpdates,
          ops
        )
    }
  }
}
