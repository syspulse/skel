package io.syspulse.skel.uri

import io.syspulse.skel.util.Util
import io.syspulse.skel.util.TimeUtil
import scala.util.Try

/* 
twitter://consumer_key:consumer_secret/access_key:access_secret@id,id,...
twitter://consumer_key:consumer_secret@id,id,id
twitter://consumer_key:consumer_secret@id,id?past=3600000&freq=30000&max=10
*/
case class TwitterURI(uri:String) {
  val PREFIX = "twitter://"

  val DEF_PAST = 1000L * 60 * 60 * 24
  val DEF_FREQ = 10000L
  val DEF_MAX = 10

  private val (_consumerKey:String,_consumerSecret:String,_accessKey:String,_accessSecret:String,
               _follow:Seq[String],_past:Long,_freq:Long,_max:Int,_ops:Map[String,String]) = parse(uri)

  def consumerKey:String = _consumerKey
  def consumerSecret:String = _consumerSecret
  def accessKey:String = _accessKey
  def accessSecret:String = _accessSecret
  def follow:Seq[String] = _follow
  def past:Long = _past
  def freq:Long = _freq
  def max:Int = _max
  def latest:Int = _ops.get("latest").map(_.toInt).getOrElse(1)

// Query is added to search query (delimit by space, but can use '|')
// Query Examples:
// -is:retweet Excludes retweets
// -is:quote Excludes quote tweets
// -is:reply
  def query:Option[String] = _ops.get("query")
  
  def ops:Map[String,String] = _ops

  def parsePast(past:String):Long = {
    if(past.isEmpty()) 
      return 1000L * 60 * 60 * 24
    
    Try(TimeUtil.humanToMillis(past)).getOrElse(DEF_PAST)
  }
  
  def parse(uri:String):(String,String,String,String,Seq[String],Long,Long,Int,Map[String,String]) = {
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
          ops.get("past").map(parsePast(_)).getOrElse(DEF_PAST),
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          ops
        )

      case consumerKey :: consumerSecret :: follow :: Nil => 
        ( Util.replaceEnvVar(consumerKey),Util.replaceEnvVar(consumerSecret),
          "","",
          follow.split(",").toSeq,
          ops.get("past").map(parsePast(_)).getOrElse(DEF_PAST),
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          ops
        )
      case consumerKey :: consumerSecret :: accessKey :: accessSecret :: follow :: Nil => 
        ( Util.replaceEnvVar(consumerKey),Util.replaceEnvVar(consumerSecret),
          Util.replaceEnvVar(accessKey),Util.replaceEnvVar(accessSecret),
          follow.split(",").toSeq,
          ops.get("past").map(parsePast(_)).getOrElse(DEF_PAST),
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          ops
        )
      
      case follow :: Nil if(!follow.isEmpty()) => 
        ( sys.env.get("CONSUMER_KEY").getOrElse(""),sys.env.get("CONSUMER_SECRET").getOrElse(""),
          sys.env.get("ACCESS_KEY").getOrElse(""),sys.env.get("ACCESS_SECRET").getOrElse(""),
          Seq(follow),
          ops.get("past").map(parsePast(_)).getOrElse(DEF_PAST),
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ) ,
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          ops
        )

      case _ => 
        ( sys.env.get("CONSUMER_KEY").getOrElse(""),sys.env.get("CONSUMER_SECRET").getOrElse(""),
          sys.env.get("ACCESS_KEY").getOrElse(""),sys.env.get("ACCESS_SECRET").getOrElse(""),
          Seq.empty,
          ops.get("past").map(parsePast(_)).getOrElse(DEF_PAST),
          ops.get("freq").map(_.toLong).getOrElse(DEF_FREQ),
          ops.get("max").map(_.toInt).getOrElse(DEF_MAX),
          ops
        )
    }    
  }
}