package io.syspulse.skel.twitter

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString

import requests._

import spray.json._

import io.syspulse.skel.Ingestable
import io.syspulse.skel.uri.TwitterURI
import io.syspulse.skel.service.JsonCommon

import spray.json._
import java.time.Instant
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import io.syspulse.skel.util.Util
import java.util.Base64
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpMethods
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.time.ZoneOffset

object TweetJson extends JsonCommon with DefaultJsonProtocol {
  // implicit val jf_twit_cont = jsonFormat3(Contributor)
  // implicit val jf_twit_coords = jsonFormat2(Coordinates)
  // implicit val jf_twit_coord = jsonFormat2(Coordinate)
  
}

trait TwitterClient[T <: Ingestable] {
  val log = Logger(s"${this}")

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
    
    log.info(s"Login -> ${url}... Credentials: '${basic64}' (${basic})")

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

      log.info(s"login: ${r}")

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

  import TweetJson._
  //implicit val fmt:JsonFormat[T]  

  val tsFormatISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.000'Z'")

  def source(consumerKey:String,consumerSecret:String,accessKey:String,accessSecret:String,followUsers:Set[String]) = {
    // try to login
    val accessToken = login(consumerKey,consumerSecret) match {
      case Success(token) => token
      case Failure(e) => throw e
    }    
    
    val ts0 = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30).minusHours(1).format(tsFormatISO)
    val ts1 = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30).format(tsFormatISO)

    //val followId = followUsers.map(_.toLong)
    //val users = followId.map(id => (id -> id.toString)).toMap
    val slug = URLEncoder.encode(
      s"(${followUsers.map(u => s"from:${u}").mkString(" OR ")})",
      StandardCharsets.UTF_8.toString()
    )

    val req = HttpRequest(
      //uri = s"${twitterUrlSearch}/stream?tweet.fields=id,source,text,username&expansions=author_id",
      uri = s"${twitterUrlSearch}/recent?query=${slug}&tweet.fields=created_at&expansions=author_id&user.fields=created_at&start_time=${ts0}&end_time=${ts1}",
      method = HttpMethods.GET,
      headers = Seq(RawHeader("Authorization",s"Bearer ${accessToken}"))
    )

    val f = Http()
    .singleRequest(req)
    .flatMap(res => { 
      res.status match {
        case StatusCodes.OK => 
          val body = res.entity.dataBytes.runReduce(_ ++ _)
          Future(Source.future(body))
        case _ => 
          val body = Await.result(res.entity.dataBytes.runReduce(_ ++ _),FiniteDuration(1000L,TimeUnit.MILLISECONDS)).utf8String
          log.error(s"${req}: ${res.status}: body=${body}")
          throw new Exception(s"${req}: ${res.status}")
          // not really reachable... But looks extra-nice :-/
          Future(Source.future(Future(ByteString(body))))
      }      
    })

    val s0 = Source.futureSource { f }

    // if(frameDelimiter.isEmpty())
    //   s0
    // else
    //   s0.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))   

    s0
  }

}

class FromTwitter[T <: Ingestable](uri:String) extends TwitterClient[T] {
  val twitterUri = TwitterURI(uri)
    
  def source():Source[ByteString,_] = 
    source(twitterUri.consumerKey,twitterUri.consumerSecret,
           twitterUri.accessKey,twitterUri.accessSecret,
           twitterUri.follow.toSet)
}
