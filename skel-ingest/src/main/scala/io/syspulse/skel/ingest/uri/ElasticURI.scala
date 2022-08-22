package io.syspulse.skel.ingest.uri

/* 
elastic://user:pass@host:port/index
*/
case class ElasticURI(uri:String) {
  val PREFIX = "elastic://"

  def url:String = {
    uri.stripPrefix(PREFIX).split("[@/]").toList match {
      case user :: host :: index :: _ => host
      case host :: index :: Nil => host
      case host :: Nil => host
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