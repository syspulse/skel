package io.syspulse.skel.twitter

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.{ExecutionContext,Future,Await}

import io.syspulse.skel.Ingestable
import io.syspulse.skel.uri.TwitterURI
import scala.concurrent.duration.FiniteDuration

class TwitterConnect(uri:String) extends TwitterClient {

  val twitterUri = TwitterURI(uri)

  def getChannels():Set[String] = Set()
  // try to login
  val accessToken = login(twitterUri.consumerKey,twitterUri.consumerSecret) match {
    case Success(token) => token
    case Failure(e) => throw e
  }

  def request(followUsers:Set[String])(implicit ec: ExecutionContext):Future[String] = {
    request(followUsers,twitterUri.past,twitterUri.max,accessToken)
      .map(_.utf8String)
  }

  def ask(followUsers:Set[String])(implicit ec: ExecutionContext,timeout:FiniteDuration):String = {
    val f = request(followUsers)
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