package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/* 
es://user:pass@host:port/index?key=value&key2=value2
es://localhost:9200/index
ess://localhost:9300
*/
case class ElasticURI(uri:String) {
  val PREFIX = Seq("es://","ess://")
  
  private val (_user:Option[String],_pass:Option[String],_url:String,_index:String,_ops:Map[String,String]) = parse(uri)

  def url:String = _url
  def user:Option[String] = _user
  def pass:Option[String] = _pass
  def index:String = _index
  def ops:Map[String,String] = _ops
  
  def parse(uri:String):(Option[String],Option[String],String,String,Map[String,String]) = {
    // resolve options
    val (url0:String,ops:Map[String,String]) = uri.split("[\\?&]").toList match {
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

    val (urlPrefix,url1) = url0.split("://").toList match {
      case "ess" :: url :: Nil => ("https://",url)
      case "es" :: url :: Nil => ("http://",url)
      case "ess" :: Nil => ("https://","localhost:9300")
      case "es" :: Nil => ("http://","localhost:9200")
      case _ => ("http://",url0)
    }

    val (user,pass,url2) = url1.split("@").toList match {
      case userPass :: url :: Nil => 
        val (user,pass) = userPass.split(":").toList match {
          case user :: pass :: Nil => (Util.resolveEnvVar(user),Util.resolveEnvVar(pass))
          case _ => (None,None)
        }
        (user,pass,Util.replaceEnvVar(url))
      
      case url :: Nil => (Util.resolveEnvVar("{ELASTIC_USER}"),Util.resolveEnvVar("{ELASTIC_PASS}"),Util.replaceEnvVar(url))
      
      case _ => (Util.resolveEnvVar("{ELASTIC_USER}"),Util.resolveEnvVar("{ELASTIC_PASS}"),"localhost:9200")
    }
    
    url2.split("/").toList match {
      case url :: index :: Nil => 
        ( 
          user,
          pass,
          urlPrefix + url,
          index,
          ops
        )
      

      case _ => 
        ( 
          user,
          pass,
          urlPrefix + url2,
          "index",
          ops
        )
    }    
  }
}