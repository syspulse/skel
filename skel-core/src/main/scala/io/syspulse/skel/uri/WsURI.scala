package io.syspulse.skel.uri

/* 
Standard WebSocket URI does not support ?and&, so use it for headers
ws://host:port/path?header1=value1,header2=value2
*/
case class WsURI(uri:String) {
  val PREFIX = "ws://"

  private val (_url:String,_ops:Map[String,String]) = parse(uri)

  def url:String = _url
  def ops:Map[String,String] = _ops
  def auth:Option[String] = _ops.get("auth")  

  def parse(uri:String):(String,Map[String,String]) = {
    // resolve options
    val (url:String,ops:Map[String,String]) = uri.split("[\\?&]").toList match {
      case url :: Nil => (url,Map())
      case url :: ops => 
        
        val vars = ops.flatMap(_.split("=").toList match {
          case k :: Nil => Some(k -> "")
          case k :: v :: Nil => Some(k -> v)
          case _ => None
        }).toMap
        
        (url,vars)
      case _ => 
        ("",Map())
    }

    (url,ops)
  }
}