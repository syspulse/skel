package io.syspulse.skel.uri

import io.syspulse.skel.util.Util

/* 
twitter://consumer_key:consumer_secret/access_key:access_secret@id,id,...
twitter://consumer_key:consumer_secret@id,id,id
twitter://consumer_key:consumer_secret@id,id?past=3600000&freq=30000
*/
case class TwitterURI(uri:String) {
  val PREFIX = "twitter://"

  private val (_consumerKey:String,_consumerSecret:String,_accessKey:String,_accessSecret:String,
               _follow:Seq[String],_past:Long,_freq:Long,_ops:Map[String,String]) = parse(uri)

  def consumerKey:String = _consumerKey
  def consumerSecret:String = _consumerSecret
  def accessKey:String = _accessKey
  def accessSecret:String = _accessSecret
  def follow:Seq[String] = _follow
  def past:Long = _past
  def freq:Long = _freq
  def ops:Map[String,String] = _ops
  
  def parse(uri:String):(String,String,String,String,Seq[String],Long,Long,Map[String,String]) = {
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
      case consumerKey :: consumerSecret :: Nil => 
        ( Util.replaceEnvVar(consumerKey),Util.replaceEnvVar(consumerSecret),
          "","",
          Seq.empty,
          ops.get("past").map(_.toLong).getOrElse(1000L * 60 * 60 * 24),
          ops.get("freq").map(_.toLong).getOrElse(10000L),
          ops
        )

      case consumerKey :: consumerSecret :: follow :: Nil => 
        ( Util.replaceEnvVar(consumerKey),Util.replaceEnvVar(consumerSecret),
          "","",
          follow.split(",").toSeq,
          ops.get("past").map(_.toLong).getOrElse(1000L * 60 * 60 * 24),
          ops.get("freq").map(_.toLong).getOrElse(10000L),
          ops
        )
      case consumerKey :: consumerSecret :: accessKey :: accessSecret :: follow :: Nil => 
        ( Util.replaceEnvVar(consumerKey),Util.replaceEnvVar(consumerSecret),
          Util.replaceEnvVar(accessKey),Util.replaceEnvVar(accessSecret),
          follow.split(",").toSeq,
          ops.get("past").map(_.toLong).getOrElse(1000L * 60 * 60 * 24),
          ops.get("freq").map(_.toLong).getOrElse(10000L),
          ops
        )
      
      case _ => ("","","","",Seq.empty,0,10000L,ops)
    }    
  }
}