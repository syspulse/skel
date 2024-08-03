package io.syspulse.skel.uri

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success

class TwitterURISpec extends AnyWordSpec with Matchers {
  
  "TwitterURI" should {
    
    "parse 'twitter://key1:secret1/1000'" in {
      val u = TwitterURI("twitter://key1:secret1/1000")
      u.consumerKey should === ("key1")
      u.consumerSecret should === ("secret1")
    }
    
  }

}
