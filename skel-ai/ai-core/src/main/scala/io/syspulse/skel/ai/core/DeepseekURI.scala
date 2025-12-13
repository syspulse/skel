package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

// modeles
// deepseek-chat
// deepseek-reasoner
// deepseek-coder
// deepseek-reasoner-32b
// deepseek-coder-32b
// deepseek-reasoner-1.5b
// deepseek-coder-1.5b
// deepseek-reasoner-0.7b
// deepseek-coder-0.7b

object DeepseekURI {
  val ID = "deepseek"
  val DEFAULT_MODEL = "deepseek-chat"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0
  val ENV_KEY_NAME = "DEEPSEEK_API_KEY"
}

case class DeepseekURI(uri:String) extends AiURI {
  val apiUrl = "https://api.deepseek.com"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,DeepseekURI.ENV_KEY_NAME)

  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  def ops:Map[String,String] = _ops
  
  def getModel():Option[String] = _model
  def getProvider():String = ClaudeURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = DeepseekURI.ID + "://"
  def DEFAULT_MODEL:String = DeepseekURI.DEFAULT_MODEL

}