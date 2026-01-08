package io.syspulse.skel.dash.source

import io.syspulse.skel.util.Util

/* 
dune://apiKey?limit=100&freq=30000&key=value
*/
case class DuneURI(uri:String) {
  val PREFIX = "dune://"

  private val (_apiKey:String,_ops:Map[String,String]) = parse(uri)

  def apiKey:String = _apiKey
  def limit:Int = _ops.get("limit").map(_.toInt).getOrElse(100)
  def timeout:Long = _ops.get("timeout").map(_.toLong).getOrElse(10000L)
  def ops:Map[String,String] = _ops
  
  def parse(uri:String):(String,Map[String,String]) = {
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
      // no users
      case apiKey :: Nil if(! apiKey.isEmpty()) => 
        ( Util.replaceEnvVar(apiKey),
          ops
        )

      case _ => 
        ( Util.replaceEnvVar("{DUNE_API_KEY}"),
          ops
        )
    }
  }
}