package io.syspulse.skel.twitter

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.time.ZoneOffset


import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpMethods

import requests._

import spray.json._

import io.syspulse.skel.Ingestable
import io.syspulse.skel.uri.TwitterURI
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.util.Util

case class TwitterSearchData(
  author_id:String,
  text:String,
  //edit_history_tweet_ids:Seq[String]
  id:String,
  created_at:String
)

case class TwitterSearchUser(
  created_at:String,
  name:String,
  username:String,
  id:String,  
)

case class TwitterSearchIncludes(
  users:Seq[TwitterSearchUser]
)

case class TwitterSearchMeta(
  result_count:Int
)


case class TwitterSearchRecent(
  data:Option[Seq[TwitterSearchData]],
  includes:Option[TwitterSearchIncludes],
  meta:TwitterSearchMeta
)

case class Twit(
  id:String,
  author_id:String,
  author_name:String,
  text:String,
  created_at:Long
)

object TwitJson extends JsonCommon {
  implicit val jf_twit_met_res = jsonFormat1(TwitterSearchMeta)
  implicit val jf_twit_sea_d = jsonFormat4(TwitterSearchData)
  implicit val jf_twit_sea_u = jsonFormat4(TwitterSearchUser)
  implicit val jf_twit_sea_inc = jsonFormat1(TwitterSearchIncludes)
  implicit val jf_twit_sea_rec = jsonFormat3(TwitterSearchRecent)  

  implicit val jf_twit_tw = jsonFormat5(Twit)  
}

trait TwitterClient {
  val log = Logger(s"${this}")
  
  import TwitJson._

  val twitterUrlAuth = "https://api.twitter.com/oauth2"
  val twitterUrlSearch = "https://api.twitter.com/2/tweets/search"
  
  implicit val as: akka.actor.ActorSystem = ActorSystem("ActorSystem-TwitterClient")

  //def timeout() = Duration("3 seconds")  

  def login(consumerKey:String,consumerSecret:String):Try[String] = {
    val url = s"${twitterUrlAuth}/token"

    //val basic = Base64.getEncoder().encode()
    val basic = 
      URLEncoder.encode(consumerKey, StandardCharsets.UTF_8.toString()) + ":" +
      URLEncoder.encode(consumerSecret, StandardCharsets.UTF_8.toString())

    val basic64 = Base64.getEncoder().encodeToString(basic.getBytes())
    
    log.info(s"Login -> ${url} ...")

    try {      
      val body = s"""grant_type=client_credentials"""
      val r = requests.post(
        url = url,
        headers = Seq(
          "Authorization" -> s"Basic ${basic64}",
          "Content-Type" -> "application/x-www-form-urlencoded;charset=UTF-8"
        ),
        data = body
      )      

      log.debug(s"login: ${r}")

      try {
        val auth = ujson.read(r.text())
        val accessToken = auth.obj("access_token").str
        val refreshToken = ""
        Success(accessToken)
      } catch {
        case e:Exception => 
          log.error(s"failed to parse login",e)
          Failure(e)
      }
      
    } catch {
      case e:Exception => 
        log.error(s"failed to login: ",e)
        Failure(e)
    }    
  }
  
  val tsFormatISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.000'Z'")
  val tsFormatISOParse = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")

  def getChannels():Set[String]

  def request(followUsers:Set[String],past:Long,max:Long,accessToken:String):Future[ByteString] = {
    // go into the past by speicif time hour
    //val ts0 = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30).minusHours(pastHours).format(tsFormatISO)
    val ts0 = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30 + past / 1000L).format(tsFormatISO)
    val ts1 = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30).format(tsFormatISO)

    val slug = URLEncoder.encode(
      s"(${followUsers.map(u => s"from:${u}").mkString(" OR ")})",
      StandardCharsets.UTF_8.toString()
    )

    val req = HttpRequest(
      //uri = s"${twitterUrlSearch}/stream?tweet.fields=id,source,text,username&expansions=author_id",
      uri = s"${twitterUrlSearch}/recent?query=${slug}&tweet.fields=created_at&expansions=author_id&user.fields=created_at&start_time=${ts0}&end_time=${ts1}&max_results=${max}",
      method = HttpMethods.GET,
      headers = Seq(RawHeader("Authorization",s"Bearer ${accessToken}"))
    )

    log.info(s"--> ${req.uri}")

    Http() 
    .singleRequest(req)
    .flatMap(res => { 
      res.status match {
        case StatusCodes.OK => 
          val body = res.entity.dataBytes.runReduce(_ ++ _)
          //Future(Source.future(body))
          body
        case _ => 
          val body = Await.result(res.entity.dataBytes.runReduce(_ ++ _),FiniteDuration(1000L,TimeUnit.MILLISECONDS)).utf8String
          log.error(s"${req}: ${res.status}: body=${body}")
          throw new Exception(s"${req}: ${res.status}")
          // not really reachable... But looks extra-nice :-/
          //Future(Source.future(Future(ByteString(body))))
      }      
    })
  }

  def source(consumerKey:String,consumerSecret:String,
             accessKey:String,accessSecret:String,
             //followUsers:Set[String],
             past:Long, // how far to check the past on each request (in milliseconds)
             freq:Long, // how often to check API
             max:Int,   // max results
             frameDelimiter:String,frameSize:Int) = {
    
    // try to login
    val accessToken = login(consumerKey,consumerSecret) match {
      case Success(token) => token
      case Failure(e) => throw e
    }

    // expiration check
    val freqExpire = freq
        
    // create a source
    val s0 = Source
      .tick(FiniteDuration(250,TimeUnit.MILLISECONDS),FiniteDuration(freq,TimeUnit.MILLISECONDS),() => getChannels())
      .map(fun => {
        val followUsers = fun()
        log.info(s"${followUsers} --> ${twitterUrlSearch}")
        followUsers
      })
      .filter(followUsers => followUsers.size > 0)
      .mapAsync(1)(followUsers => request(followUsers,past,max,accessToken).map((followUsers,_)))
      .map{ case(followUsers,body) => {
        log.debug(s"body='${body.utf8String}'")

        val rsp = body.utf8String.parseJson.convertTo[TwitterSearchRecent]
        
        log.info(s"${followUsers}: results=${rsp.meta.result_count}")
        
        if(rsp.meta.result_count != 0) {
          val users = rsp.includes.get.users
          val tweets = rsp.data.get.flatMap( td => {
            val userId = users.find(_.id == td.author_id)
            userId.map(u => Twit(
              id = td.id,
              author_id = td.author_id,
              author_name = u.username,
              text = td.text,
              created_at = OffsetDateTime.parse(td.created_at,tsFormatISOParse).toInstant.toEpochMilli
            ))   
          })
          .map(t => {
            log.debug(s"${t}")
            t
          })
          .groupBy(_.author_id)           
          .map{ case(authorId,tt) => {
            log.info(s"author=${authorId} (${tt.head.author_name}): tweets=(${tt.size})")
            // get latest Twit
            tt.maxBy(_.created_at)
          }}

          tweets
        } else Seq.empty
      }}
    
    // deduplication flow
    val s1 = s0
      //.groupedWithin(if(getChannels().size > 0) getChannels().size else 1,FiniteDuration(freq,TimeUnit.MILLISECONDS))
      .statefulMapConcat { () =>
        var state = List.empty[Twit]
        var lastCheckTs = System.currentTimeMillis()
        (tt) => {
          val uniq = tt.filter(t => ! state.find(_.id == t.id).isDefined)
          state =  state.prependedAll( uniq )

          val now = System.currentTimeMillis()
          val age = now - lastCheckTs
          if( age >= freqExpire ) {            
            state = state.takeWhile(t => t.created_at >= (now - past) )
            lastCheckTs = now            
          }
          log.info(s"uniq: ${uniq}")
          uniq
        }
      }     
      
    s1
  }
}

class FromTwitter(uri:String) extends TwitterClient {
  val twitterUri = TwitterURI(uri)
  import TwitJson._
 
  def getChannels() = twitterUri.follow.toSet

  def source(frameDelimiter:String="\n",frameSize:Int = 8192):Source[ByteString,_] = {
    val s1 = source(twitterUri.consumerKey,twitterUri.consumerSecret,
           twitterUri.accessKey,twitterUri.accessSecret,
           //followUsers = twitterUri.follow.toSet,
           twitterUri.past,
           twitterUri.freq,
           twitterUri.max,
           frameDelimiter,frameSize)

    // convert back to String to be Pipeline Compatible
    // ATTENTION: On previous step it was not needed to json-ize
    //            doing it only for consistency
    // NOTE: Insert `\n` (newline) for Pipeline compatibility
    val s2 = s1.map( t => ByteString(s"${t.toJson.compactPrint}\n"))

    if(frameDelimiter.isEmpty())
      s2
    else
      s2.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))   
  }
}

