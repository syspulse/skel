package io.syspulse.skel.uri

/* 
elastic://user:pass@host:port/index
*/
case class ElasticURI(uri:String) {
  val PREFIX = "elastic://"

  def url:String = {
    val prefix = "http://"

    uri.stripPrefix(PREFIX).split("[@/]").toList match {
      case user :: host :: index :: _ => prefix + host
      case host :: index :: Nil => prefix + host
      case host :: Nil => prefix + host
      case _ => ""
    }
  }

  def index:String = {
    uri.stripPrefix(PREFIX).split("[@/]").toList match {
      case user :: host :: index :: _ => index
      case host :: index :: Nil => index
      case host :: Nil => ""
      case _ => ""
    }
  }
}