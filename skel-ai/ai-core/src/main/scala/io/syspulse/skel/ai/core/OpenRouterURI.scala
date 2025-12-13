package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

// modeles

object OpenRouterURI {
  val ID = "openrouter"
  val DEFAULT_MODEL = "openai/gpt-4o-mini"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0
  val ENV_KEY_NAME = "OPENROUTER_API_KEY"
}

case class OpenRouterURI(uri:String) extends AiURI {
  val apiUrl = "https://openrouter.ai/api"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,OpenRouterURI.ENV_KEY_NAME)

  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  
  override def getModel(model:String):String = {
    if(model.contains(":")) model else ext.map(e => s"${model}:${e}").getOrElse(model)
  }

  def ops:Map[String,String] = _ops
  
  def getModel():Option[String] = _model
  def getProvider():String = OpenRouterURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = OpenRouterURI.ID + "://"
  def DEFAULT_MODEL:String = OpenRouterURI.DEFAULT_MODEL

}