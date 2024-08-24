package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/*
  http://host:port/url
  http://GET@host:port/url
  http://POST@host:port/url
  http://POST@123456789@host:port/url
  http://POST@{VAR}@host:port/url
  
  http://GET:ASYNC@host:port/url
  http://POST:ASYNC@host:port/url
  replace {} with real data
*/
case class HttpURI(uri0:String,authDefault:Option[String]=None) {
  
  private val (_proto:String,_verb:String,_rest:String,_headers:Map[String,String],_body:Option[String],_async:Boolean) = parse(uri0)

  def uri:String = s"${_proto}://${_rest}"
  def verb:String = _verb
  def rest:String = _rest
  def headers:Map[String,String] = _headers
  def body:Option[String] = _body
  def async:Boolean = _async

  def parse(uri:String) = {

    def buildUri(proto:String,verb:String,rest:String,headers:Map[String,String]=Map(),body:Option[String]=None,async:Boolean=false) = {
      (proto, verb, rest, headers, body, async)
    }

    def getAuth(auth:String):Map[String,String] = {
      val authToken = 
        if(auth.startsWith("{"))
          Some(Util.replaceEnvVar(auth))
        else
        if(!auth.isEmpty())
          Some(auth)
        else
          authDefault        

      if(authToken.isDefined)
        Map("Authorization" -> s"Bearer ${authToken.get}")
      else
        Map()
    }

    uri.split("(://|@)").toList match {
      case proto :: "GET:ASYNC" :: rest :: Nil => buildUri(proto,"GET",rest,getAuth(""),async=true)
      case proto :: "POST:ASYNC" :: rest :: Nil => buildUri(proto,"POST",rest,getAuth(""),async=true)
      case proto :: "PUT:ASYNC" :: rest :: Nil => buildUri(proto,"PUT",rest,getAuth(""),async=true)
      case proto :: "DELETE:ASYNC" :: rest :: Nil => buildUri(proto,"DELETE",rest,getAuth(""),async=true)

      case proto :: "GET:ASYNC" :: auth :: rest :: Nil => buildUri(proto,"GET",rest,getAuth(auth),async=true)
      case proto :: "POST:ASYNC" :: auth  :: rest :: Nil  => buildUri(proto,"POST",rest,getAuth(auth),async=true)
      case proto :: "PUT:ASYNC" :: auth  :: rest :: Nil => buildUri(proto,"PUT",rest,getAuth(auth),async=true)
      case proto :: "DELETE:ASYNC" :: auth  :: rest :: Nil => buildUri(proto,"DELETE",rest,getAuth(auth),async=true)
      
      case proto :: "GET" :: auth :: rest :: Nil => buildUri(proto,"GET",rest,getAuth(auth))
      case proto :: "POST" :: auth  :: rest :: Nil  => buildUri(proto,"POST",rest,getAuth(auth))
      case proto :: "PUT" :: auth  :: rest :: Nil => buildUri(proto,"PUT",rest,getAuth(auth))
      case proto :: "DELETE" :: auth  :: rest :: Nil => buildUri(proto,"DELETE",rest,getAuth(auth))

      case proto :: "GET" :: rest :: Nil => buildUri(proto,"GET",rest,getAuth(""))
      case proto :: "POST" :: rest :: Nil => buildUri(proto,"POST",rest,getAuth(""))
      case proto :: "PUT" :: rest :: Nil => buildUri(proto,"PUT",rest,getAuth(""))
      case proto :: "DELETE" :: rest :: Nil => buildUri(proto,"DELETE",rest,getAuth(""))
      
      case proto :: "AGET" :: rest :: Nil => buildUri(proto,"GET",rest,getAuth(""),async=true)
      case proto :: "APOST" :: rest :: Nil => buildUri(proto,"POST",rest,getAuth(""),async=true)
      case proto :: "APUT" :: rest :: Nil => buildUri(proto,"PUT",rest,getAuth(""),async=true)
      case proto :: "ADELETE" :: rest :: Nil => buildUri(proto,"DELETE",rest,getAuth(""),async=true)

      case proto :: rest :: Nil => buildUri(proto,"GET",rest,getAuth(""))

      case u => buildUri("http","GET","localhost:8080",getAuth(""))
    }
  }
  
}