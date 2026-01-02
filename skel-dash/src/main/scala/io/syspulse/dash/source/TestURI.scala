package io.syspulse.dash.source

import io.syspulse.skel.util.Util

/* 
dune://apiKey?limit=100&freq=30000&key=value
*/
case class TestURI(uri:String) {
  val PREFIX = "test://"

  private val (_ops:Map[String,String]) = parse(uri)

  def delay:Long = _ops.get("delay").map(_.toLong).getOrElse(100L)
  def threads:Int = _ops.get("threads").map(_.toInt).getOrElse(8)
  def async:Boolean = _ops.get("async").map(_.toBoolean).getOrElse(true)
  def rsp:Option[String] = _ops.get("rsp")
  def ops:Map[String,String] = _ops
  
  def parse(uri:String):(Map[String,String]) = {
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
      case _ => 
        ( ops )
    }
  }
}