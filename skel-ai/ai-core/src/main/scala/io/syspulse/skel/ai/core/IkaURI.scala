package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

// modeles

object IkaURI {
  val ID = "ika"
  val DEFAULT_MODEL = "openai/gpt-4o-mini"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0 
  val DEF_APP_ID = "IKA_APP_ID"
}

case class IkaURI(uri:String) extends AiURI {
  val apiUrl = "http://localhost:8080/api/v1/ika"

  private val (_app:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,IkaURI.DEF_APP_ID)

  def app:String = _app
  override def model:Option[String] = _model
  
  override def getModel(model:String):String = {
    if(model.contains(":")) model else ext.map(e => s"${model}:${e}").getOrElse(model)
  }

  def ops:Map[String,String] = _ops
  
  def getModel():Option[String] = _model
  def getProvider():String = IkaURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = IkaURI.ID + "://"
  def DEFAULT_MODEL:String = IkaURI.DEFAULT_MODEL

}