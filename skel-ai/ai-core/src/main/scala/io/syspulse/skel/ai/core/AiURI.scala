package io.syspulse.skel.ai.core

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.core.{OpenAiURI,VeniceURI,GrokURI,GeminiURI}
import io.syspulse.skel.ai.core.{AiURI => _model}

trait AiURI {
  val apiUrl:String

  override def toString():String = s"${this.getClass.getSimpleName}(${getProvider()},${model.getOrElse("")},${Util.trunc(apiKey,8)},${getOptions().mkString(",")})"

  def getModel():Option[String]
  def getModel(model:String):String = model
  def getProvider():String

  // enrich request from URI param
  def getTools():Seq[AiTool] = Seq.empty

  def getOptions():Map[String,String]

  def apiKey:String = "" //getOptions().get("apiKey").getOrElse("")
  def model:Option[String] = getOptions().get("model")

  // conversation id (thread_id / response_id)
  def timeout:Long = getOptions().get("timeout").map(_.toLong).getOrElse(30000)
  def retry:Int = getOptions().get("retry").map(_.toInt).getOrElse(3)
  def tid:Option[String] = getOptions().get("tid")
  def temperature:Option[Double] = getOptions().get("temperature").map(_.toDouble)
  def topP:Option[Double] = getOptions().get("top_p").map(_.toDouble)
  def maxTokens:Option[Int] = getOptions().get("max_tokens").map(_.toInt)
  // ext is plugin extension in model name (e.g. "gpt-4o:web")
  // it is needed since API respnse may remove it from model name
  def ext:Option[String] = getModel().flatMap(_.split(":").drop(1).headOption)
  def output:Option[String] = getOptions().get("output")

  def DEFAULT_MODEL:String
  protected def getPrefix():String
  
  // system prompt
  def system:Option[String] = getOptions().get("sys").orElse(getOptions().get("system"))
    .map(s => s.split("://").toList match {
      case "file" :: file :: Nil =>
        os.read(os.Path(file,os.pwd))
      case s :: Nil => s
      case _ => throw new Exception(s"unsupported uri: ${s}")
    })

  // user prompt
  def prompt:Option[String] = getOptions().get("prompt").orElse(getOptions().get("ask"))
    .map(s => s.split("://").toList match {
      case "file" :: file :: Nil =>
        os.read(os.Path(file,os.pwd))
      case s :: Nil => s
      case _ => throw new Exception(s"unsupported uri: ${s}")
    })
    
  def parse(uri:String,envKeyName:String):(String,Option[String],Map[String,String]) = {

    // resolve options
    val (url:String,ops:Map[String,String]) = uri.split("[\\?&]").toList match {
      case url :: Nil => (url,Map())
      case url :: ops => 
        
        val vars = ops.flatMap(_.split("=").toList match {
          case k :: v :: Nil => 
            // kubernetes $(ENV) should be parsed here
            val v1 = Util.replaceEnvVar(v)
            Some(k -> v1)
          case _ => None
        }).toMap
        
        (url,vars)
      case _ => 
        ("",Map())
    }
    
    val rr = url.stripPrefix(getPrefix()).split("[@]").toList match {
      case "" :: Nil =>
        ( sys.env.get(envKeyName).getOrElse(""),Some(DEFAULT_MODEL),ops
        )

      case model :: Nil =>         
        ( sys.env.get(envKeyName).getOrElse(""),Some(model),ops
        )

      case apiKey :: model :: Nil => 
        ( Util.replaceEnvVar(apiKey),Some(model),ops
        )      
            
      case _ =>
        ( sys.env.get(envKeyName).getOrElse(""),Some(DEFAULT_MODEL),ops
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

/* 
openai://
*/
object AiURI {
  def apply(uri:String):AiURI = {
    uri.split("://").toList match {
      case OpenAiURI.ID :: _ => OpenAiURI(uri)
      case VeniceURI.ID :: _ => VeniceURI(uri)
      case GrokURI.ID :: _ => GrokURI(uri)
      case GeminiURI.ID :: _ => GeminiURI(uri)
      case ClaudeURI.ID :: _ => ClaudeURI(uri)
      case DeepseekURI.ID :: _ => DeepseekURI(uri)
      case OpenRouterURI.ID :: _ => OpenRouterURI(uri)
      case _ => throw new IllegalArgumentException(s"Unknown AI provider: '${uri}'")
    }
  }  
}