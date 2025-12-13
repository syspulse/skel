package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

// Models:
// venice-uncensored
// mistral-31-24b
// qwen3-235b
// qwen3-4b
// venice-sd35

object VeniceURI {
  val ID = "venice"
  val DEFAULT_MODEL = "venice-uncensored"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0
  val ENV_KEY_NAME = "VENICE_API_KEY"
}

case class VeniceURI(uri:String) extends AiURI {
  val apiUrl = "https://api.venice.ai/api"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,VeniceURI.ENV_KEY_NAME)
  
  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  def ops:Map[String,String] = _ops  
  
  def getModel():Option[String] = _model
  def getProvider():String = VeniceURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = VeniceURI.ID + "://"
  def DEFAULT_MODEL:String = VeniceURI.DEFAULT_MODEL
}