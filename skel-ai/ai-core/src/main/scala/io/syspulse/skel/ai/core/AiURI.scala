package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.openai.OpenAiURI

trait AiURI {
  def getModel():Option[String]
  def getProvider():String

  def getOptions():Map[String,String]

  // conversation id (thread_id / response_id)
  def timeout:Long = getOptions().get("timeout").map(_.toLong).getOrElse(30000)
  def retry:Int = getOptions().get("retry").map(_.toInt).getOrElse(3)
  def tid:Option[String] = getOptions().get("tid")
  def temperature:Option[Double] = getOptions().get("temperature").map(_.toDouble)
  def topP:Option[Double] = getOptions().get("top_p").map(_.toDouble)
  def maxTokens:Option[Int] = getOptions().get("max_tokens").map(_.toInt)
}

/* 
openai://
*/
object AiURI {
  def apply(uri:String):AiURI = {
    uri.split("://").toList match {
      case "openai" :: _ => OpenAiURI(uri)
      case _ => throw new IllegalArgumentException(s"Unknown AI provider: ${uri}")
    }
  }
}