package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/* 
redis://user:pass@host:port/db    
redis://user:pass@host:port/db/channel  - PubSub subscription
*/
object RedisURI {
  val DEF_TIMEOUT = 10000L
  val DEF_HOST = "localhost"
  val DEF_PORT = 6379
  val DEF_URL = s"${DEF_HOST}:${DEF_PORT}"  
  val DEF_DB = 0
  val DEF_INDEX = 0
}

case class RedisURI(uri:String) {
  val PREFIX = "redis://"
  
  private val (_user:Option[String],
              _pass:Option[String],
              _host:String,
              _port:Int,
              _index:Int,
              _subscription:Option[String],
              _ops:Map[String,String]) = parse(uri)

  def host:String = _host
  def port:Int = _port
  def user:Option[String] = _user
  def pass:Option[String] = _pass
  def db:Int = _index
  def subscription:Option[String] = _subscription
  def timeout:Long = ops.get("timeout").map(_.toLong).getOrElse(RedisURI.DEF_TIMEOUT)
  def ops:Map[String,String] = _ops
  
  def parse(uri:String):(Option[String],Option[String],String,Int,Int,Option[String],Map[String,String]) = {
    def urlToHostPort(url:String):(String,Int) = {
      url.split(":").toList match {
        case host :: port :: Nil => (host,port.toInt)
        case host :: Nil => (host,RedisURI.DEF_PORT)
        case _ => (RedisURI.DEF_HOST,RedisURI.DEF_PORT)
      }
    }

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
      case "redis" :: url :: Nil => ("",url)
      case "redis" :: Nil => ("",RedisURI.DEF_URL)
      case _ => ("",RedisURI.DEF_URL)
    }

    val (user,pass,url2) = url1.split("@").toList match {
      case userPass :: url :: Nil => 
        val (user,pass) = userPass.split(":").toList match {
          case user :: pass :: Nil => (Util.resolveEnvVar(user),Util.resolveEnvVar(pass))
          case _ => (None,None)
        }
        (user,pass,Util.replaceEnvVar(url))
      
      case url :: Nil => (Util.resolveEnvVar("{REDIS_USER}"),Util.resolveEnvVar("{REDIS_PASS}"),Util.replaceEnvVar(url))
      
      case _ => (Util.resolveEnvVar("{REDIS_USER}"),Util.resolveEnvVar("{REDIS_PASS}"),RedisURI.DEF_URL)
    }
    
    url2.split("/").toList match {
      case url :: index :: channel :: Nil => 
        val (host,port) = urlToHostPort(url)
        ( 
          user,
          pass,
          host,
          port,
          index.toInt,
          Some(channel),
          ops
        )

      case url :: index :: Nil => 
        val (host,port) = urlToHostPort(url)
        ( 
          user,
          pass,
          host,
          port,
          index.toInt,
          None,
          ops
        )
      
      case _ => 
        val (host,port) = urlToHostPort(url2)
        ( 
          user,
          pass,
          host,
          port,
          RedisURI.DEF_INDEX,
          None,
          ops
        )
    }    
  }
}