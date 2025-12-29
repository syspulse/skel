package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

object MirrorURI {
  val ID = "mirror"
}

case class MirrorURI(uri:String) extends AiURI {
  val apiUrl = ""

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,ClaudeURI.ENV_KEY_NAME)

  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  def ops:Map[String,String] = _ops
  
  def getModel():Option[String] = _model
  def getProvider():String = ClaudeURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = MirrorURI.ID + "://"
  def DEFAULT_MODEL:String = ""

}