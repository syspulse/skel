package io.syspulse.skel.ai.source.openai

import io.syspulse.skel.util.Util

/* 
openai:// - Key is taken from $OPENAI_API_KEY
openai://<api_key>
openai://<api_key>@<model>
*/
case class OpenAiURI(uri:String) {
  val PREFIX = "openai://"

  private val (_apiKey:String,_model:String,_ops:Map[String,String]) = parse(uri)

  def apiKey:String = _apiKey
  def model:String = _model
  def ops:Map[String,String] = _ops
  
  def parse(uri:String):(String,String,Map[String,String]) = {
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
    
    url.stripPrefix(PREFIX).split("[:/@]").toList match {
      case "" :: model :: Nil =>         
        ( sys.env.get("OPENAI_API_KEY").getOrElse(""),model,ops
        )

      case apiKey :: model :: Nil =>         
        ( Util.replaceEnvVar(apiKey),model,ops
        )
      
      case "" :: Nil =>      
        ( sys.env.get("OPENAI_API_KEY").getOrElse(""),"gpt-4o-mini",Map()
        )

      case apiKey :: Nil => 
        ( Util.replaceEnvVar(apiKey),"gpt-4o-mini",ops          
        )
      
      case _ =>      
        ( sys.env.get("OPENAI_API_KEY").getOrElse(""),"gpt-4o-mini",Map()
        )
    }    
  }
}