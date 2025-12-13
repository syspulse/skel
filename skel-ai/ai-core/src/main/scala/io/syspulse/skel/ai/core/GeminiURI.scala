package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

// modeles
// gemini-1.5-pro
// gemini-1.5-flash
// gemini-pro

object GeminiURI {
  val ID = "gemini"
  val DEFAULT_MODEL = "gemini-2.0-flash" //"gemini-2.5-flash-lite"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0
  val ENV_KEY_NAME = "GEMINI_API_KEY"
}

case class GeminiURI(uri:String) extends AiURI {
  // val apiUrl = "https://generativelanguage.googleapis.com"
  val apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,GeminiURI.ENV_KEY_NAME)

  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  def ops:Map[String,String] = _ops
  
  def getModel():Option[String] = _model
  def getProvider():String = GeminiURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = GeminiURI.ID + "://"
  def DEFAULT_MODEL:String = GeminiURI.DEFAULT_MODEL

}
