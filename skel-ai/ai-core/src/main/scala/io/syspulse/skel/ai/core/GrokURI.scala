package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

// modeles
// grok-4-latest
// grok-4-fast-reasoning
// grok-4-fast-non-reasoning
// grok-code-fast-1
// grok-3-mini
// grok-3

object GrokURI {
  val ID = "grok"
  val DEFAULT_MODEL = "grok-4-latest"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0
  val ENV_KEY_NAME = "GROK_API_KEY"
}

case class GrokURI(uri:String) extends AiURI {
  val apiUrl = "https://api.x.ai"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,GrokURI.ENV_KEY_NAME)

  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  def ops:Map[String,String] = _ops
  
  def getModel():Option[String] = _model
  def getProvider():String = GrokURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = GrokURI.ID + "://"
  def DEFAULT_MODEL:String = GrokURI.DEFAULT_MODEL

}