package io.syspulse.skel.uri

/* 
elastic://user:pass@host:port/index
*/
case class ElasticURI(uri:String) {
  val PREFIX = "elastic://"

  val (eurl:String,eindex:String) = parse(uri)

  def url:String = eurl
  def index:String = eindex

  def parse(uri:String):(String,String) = {
    val prefix = "http://"

    uri.stripPrefix(PREFIX).split("[@/]").toList match {
      case user :: host :: index :: _ => (prefix + host,index)
      case host :: index :: Nil => (prefix + host,index)
      case host :: Nil => (prefix + host,"")
      case _ => ("http://localhost:9200","")
    }
  }
}