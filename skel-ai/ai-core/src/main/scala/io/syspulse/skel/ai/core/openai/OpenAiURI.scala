package io.syspulse.skel.ai.core.openai

import io.syspulse.skel.util.Util

/* 
openai:// - Key is taken from $OPENAI_API_KEY
openai://<model>
openai://<model>?<key1=value1&key2=value2...>
openai://<api_key>@<model>
*/
case class OpenAiURI(uri:String) {
  val PREFIX = "openai://"
  val DEFAULT_MODEL = "gpt-4o-mini"

  private val (_apiKey:String,_model:Option[String],_ops:Map[String,String]) = parse(uri)

  def apiKey:String = _apiKey
  def model:Option[String] = _model
  def ops:Map[String,String] = _ops
  
  def parse(uri:String):(String,Option[String],Map[String,String]) = {
    // resolve options
    val (url:String,ops:Map[String,String]) = uri.split("[\\?&]").toList match {
      case url :: Nil => (url,Map())
      case url :: ops => 
        
        val vars = ops.flatMap(_.split("=").toList match {
          case k :: v :: Nil => Some(k -> v)
          case _ => None
        }).toMap
        
        (url,vars)
      case _ => 
        ("",Map())
    }
    
    val rr = url.stripPrefix(PREFIX).split("[@]").toList match {
      case "" :: Nil =>      
        ( sys.env.get("OPENAI_API_KEY").getOrElse(""),Some(DEFAULT_MODEL),ops
        )

      case model :: Nil =>         
        ( sys.env.get("OPENAI_API_KEY").getOrElse(""),Some(model),ops
        )

      case apiKey :: model :: Nil =>         
        ( Util.replaceEnvVar(apiKey),Some(model),ops
        )      
            
      case _ =>      
        ( sys.env.get("OPENAI_API_KEY").getOrElse(""),Some(DEFAULT_MODEL),ops
        )
    }

    ops.get("apiKey") match {
      case Some(apiKey) =>
        (apiKey,rr._2,rr._3)
      case None =>
        rr
    }
  }
}