package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/* 
twitter://consumer_key:consumer_secret/access_key:access_secret/id,id,...
twitter://consumer_key:consumer_secret/id,id,...
*/
case class TwitterURI(uri:String) {
  val PREFIX = "twitter://"

  private val (_consumerKey:String,_consumerSecret:String,_accessKey:String,_accessSecret:String,_follow:Seq[String]) = parse(uri)

  def consumerKey:String = _consumerKey
  def consumerSecret:String = _consumerSecret
  def accessKey:String = _accessKey
  def accessSecret:String = _accessSecret
  def follow:Seq[String] = _follow
  
  def parse(uri:String):(String,String,String,String,Seq[String]) = {
    // resolve options
    val (url:String,ops:String) = uri.split("\\?").toList match {
      case url :: Nil => (url,"")
      case url :: ops :: Nil => (url,ops)      
      case _ => ("","")
    }
    
    url.stripPrefix(PREFIX).split("[:/]").toList match {
      case consumerKey :: consumerSecret :: follow :: Nil => 
        ( Util.replaceEnvVar(consumerKey),Util.replaceEnvVar(consumerSecret),
          "","",
          follow.split(",").toSeq
        )
      case consumerKey :: consumerSecret :: accessKey :: accessSecret :: follow :: Nil => 
        ( Util.replaceEnvVar(consumerKey),Util.replaceEnvVar(consumerSecret),
          Util.replaceEnvVar(accessKey),Util.replaceEnvVar(accessSecret),
          follow.split(",").toSeq
        )
      
      case _ => ("","","","",Seq.empty)
    }
  }
}