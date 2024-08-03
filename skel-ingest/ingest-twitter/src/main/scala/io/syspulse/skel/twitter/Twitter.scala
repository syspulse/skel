package io.syspulse.skel.twitter

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.Ingestable
import io.syspulse.skel.uri.TwitterURI

object Twitter {

  def fromTwitter[T <: Ingestable](uri:String) = {
    val twitter = new FromTwitter[T](uri)
    twitter.source()
  }

}