package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.ai.core.Providers

/* 
openai:// - Key is taken from $OPENAI_API_KEY
openai://<model>
openai://<model>?<key1=value1&key2=value2...>
openai://<api_key>@<model>
*/
object OpenAiURI {
  val ID = "openai"
  val DEFAULT_MODEL = "gpt-4o-mini"
  val DEFAULT_TEMPERATURE = 0.7
  val DEFAULT_TOP_P = 1.0
  val ENV_KEY_NAME = "OPENAI_API_KEY"
}

case class OpenAiURI(uri:String) extends AiURI {
  val apiUrl = "https://api.openai.com"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri,OpenAiURI.ENV_KEY_NAME)

  override def apiKey:String = _apiKey
  override def model:Option[String] = _model
  def ops:Map[String,String] = _ops
  def vdb:Option[String] = _ops.get("vdb")
  def org:Option[String] = _ops.get("org")  // org
  def aid:Option[String] = _ops.get("aid")  // agent ID
  
  def getModel():Option[String] = _model
  def getProvider():String = OpenAiURI.ID

  def getOptions():Map[String,String] = _ops

  def getPrefix():String = OpenAiURI.ID + "://"
  def DEFAULT_MODEL:String = OpenAiURI.DEFAULT_MODEL

  override def getTools():Seq[AiTool] = {
    ops.get("tools").map(_.split(",").map(t => t.trim match {
      case "web_search" => AiTool(`type` = "web_search")      
      case _ => throw new IllegalArgumentException(s"Unknown tool type: ${t}")
    }).toSeq).getOrElse(Seq.empty)
      
  }

}