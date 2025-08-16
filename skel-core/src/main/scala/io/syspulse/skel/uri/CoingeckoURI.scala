package io.syspulse.skel.uri

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import io.syspulse.skel.util.Util

/* 
coingecko://apiKey@id,id,...
*/

case class CoingeckoURI(uri:String) {
  val PREFIX_PRO = "coingecko"
  val PREFIX_FREE = "cg"

  private val (_apiKey:String,_max:Int,_ops:Map[String,String],prefix:String) = parse(uri)
  

  def apiKey:String = _apiKey
  def max:Int = _max
  // def latest:Int = _ops.get("latest").map(_.toInt).getOrElse(1)
  def ops:Map[String,String] = _ops
  def throttle:Long = _ops.get("throttle").map(_.toLong).getOrElse(30000L)
  def timeout:FiniteDuration = _ops.get("timeout").map(_.toLong).map(FiniteDuration(_,TimeUnit.MILLISECONDS)).getOrElse(FiniteDuration(10000L,TimeUnit.MILLISECONDS))
  
  def getBaseUrl():String = prefix match {
    case PREFIX_PRO => "https://pro-api.coingecko.com/api/v3"
    case PREFIX_FREE => "https://api.coingecko.com/api/v3"
    case "" => "https://api.coingecko.com/api/v3"
    case _ => prefix
  }
  
  def parse(uri:String):(String,Int,Map[String,String],String) = {
    
    var (prefix,url1) = uri.split("://").toList match {
      case PREFIX_PRO :: url =>  (PREFIX_PRO,url.headOption.getOrElse(""))
      case PREFIX_FREE :: url => (PREFIX_FREE,url.headOption.getOrElse(""))
      case prefix :: url => ("", uri)
      case _ => (PREFIX_FREE,"")
    }
    
    // resolve options
    val (url:String,ops:Map[String,String]) = url1.split("[\\?&]").toList match {
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
    
    val (apiKey, max, ops1) = url.split("[:/@]").toList match {
      case _ if prefix == "" => 
        // speical case for http:// style uri (apiKey is not supported in URL, only as ops
        prefix = url
        (
          ops.get("apiKey").getOrElse(sys.env.get("COINGECKO_API_KEY").orElse(sys.env.get("CG_API_KEY")).getOrElse("")),
          ops.get("max").map(_.toInt).getOrElse(10),
          ops
        )
      case apiKey :: _ =>
        (
          Util.replaceEnvVar(apiKey),
          ops.get("max").map(_.toInt).getOrElse(10),
          ops
        )
      case _ =>
        (
          sys.env.get("COINGECKO_API_KEY").orElse(sys.env.get("CG_API_KEY")).getOrElse(""),
          ops.get("max").map(_.toInt).getOrElse(10),
          ops
        )
    }

    (apiKey,max,ops1,prefix)
  }
}