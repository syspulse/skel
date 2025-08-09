package io.syspulse.skel.coingecko

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import io.syspulse.skel.util.Util

/* 
coingecko://apiKey@id,id,...
*/

case class CoingeckoURI(uri:String) {
  val PREFIX = "coingecko://"

  private val (_apiKey:String,_max:Int,_ops:Map[String,String]) = parse(uri)
  

  def apiKey:String = _apiKey
  def max:Int = _max
  // def latest:Int = _ops.get("latest").map(_.toInt).getOrElse(1)
  def ops:Map[String,String] = _ops
  def timeout:FiniteDuration = _ops.get("timeout").map(_.toLong).map(FiniteDuration(_,TimeUnit.MILLISECONDS)).getOrElse(FiniteDuration(10000L,TimeUnit.MILLISECONDS))
  
  def parse(uri:String):(String,Int,Map[String,String]) = {
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
      case apiKey :: _ =>
        (
          Util.replaceEnvVar(apiKey),
          ops.get("max").map(_.toInt).getOrElse(10),
          ops
        )
      case _ =>
        (
          sys.env.get("COINGECKO_API_KEY").getOrElse(""),
          ops.get("max").map(_.toInt).getOrElse(10),
          ops
        )
    }
  }
}