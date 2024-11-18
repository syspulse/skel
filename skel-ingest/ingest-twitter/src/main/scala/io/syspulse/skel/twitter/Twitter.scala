package io.syspulse.skel.twitter

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.{ExecutionContext,Future,Await}

import io.syspulse.skel.Ingestable
import io.syspulse.skel.uri.TwitterURI
import scala.concurrent.duration.FiniteDuration

import spray.json._
import java.time.OffsetDateTime

class TwitterConnect(uri:String) extends TwitterClient {
  import TwitJson._
  
  val twitterUri = TwitterURI(uri)

  def getChannels():Set[String] = Set()
  // try to login
  val accessToken = login(twitterUri.consumerKey,twitterUri.consumerSecret) match {
    case Success(token) => token
    case Failure(e) => throw e
  }

  def request(followUsers:Set[String])(implicit ec: ExecutionContext):Future[Seq[Twit]] = {
    request(followUsers,twitterUri.past,twitterUri.max,accessToken)
      .map(_.utf8String)      
      .map(body => {
        val rsp = body.parseJson.convertTo[TwitterSearchRecent]        
        log.debug(s"${followUsers}: results=${rsp.meta.result_count}")
        
        if(rsp.meta.result_count != 0) {
          val users = rsp.includes.get.users
          rsp.data.get.flatMap( td => {
            val userId = users.find(_.id == td.author_id)
            userId.map(u => Twit(
              id = td.id,
              author_id = td.author_id,
              author_name = u.username,
              text = td.text,
              created_at = OffsetDateTime.parse(td.created_at,tsFormatISOParse).toInstant.toEpochMilli
            ))
          })
        } else Seq()
      })
  }

  def ask(followUsers:Set[String])(implicit ec: ExecutionContext,timeout:FiniteDuration):Seq[Twit] = {
    val f = request(followUsers)
    Await.result(f,timeout)
  }

  def ask()(implicit ec: ExecutionContext,timeout:FiniteDuration):Seq[Twit] = {
    val f = request(twitterUri.follow.toSet)
    Await.result(f,timeout)
  }

}

object Twitter {

  def fromTwitter(uri:String) = {
    val twitter = new FromTwitter(uri)
    twitter.source()
  }

  def connect(uri:String):Try[TwitterClient] = {
    try {
      val client = new TwitterConnect(uri)
      Success(client)

    } catch {
      case e:Exception => return Failure(e)
    }
  }
}