package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

// modeles
// claude-sonnet-4-5
// claude-sonnet-3
// claude-haiku-4-5
// claude-haiku-3
// claude-opus-4-5
// claude-opus-3

// https://portkey.ai/models

object ClaudeURI {
  val ID = "claude"
  val DEFAULT_MODEL = "claude-opus-4-0" // "claude-sonnet-3-7"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0
  val ENV_KEY_NAME = "CLAUDE_API_KEY"
  val DEF_CACHE = Some("ephemeral")
}

case class ClaudeURI(uri:String) extends AiURI {
  val apiUrl = "https://api.anthropic.com"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,ClaudeURI.ENV_KEY_NAME)

  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  def ops:Map[String,String] = _ops

  override def version:Option[String] = _ops.get("version").orElse(Some("2023-06-01"))
  
  def getModel():Option[String] = _model
  def getProvider():String = ClaudeURI.ID

  override def getCache():Option[String] = ClaudeURI.DEF_CACHE

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = ClaudeURI.ID + "://"
  def DEFAULT_MODEL:String = ClaudeURI.DEFAULT_MODEL

}