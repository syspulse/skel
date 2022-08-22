package io.syspulse.skel.ingest.uri

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success

class UriSpec extends AnyWordSpec with Matchers {
  
  "ElasticUri" should {
    "url ('elastic://host:port/index') -> 'host:port'" in {
      val s = ElasticURI("elastic://host:port/index")
      s.url should === ("host:port")
    }

    "url ('elastic://host:port/') -> 'host:port'" in {
      val s = ElasticURI("elastic://host:port/")
      s.url should === ("host:port")
    }

    "url ('elastic://host:port') -> 'host:port'" in {
      val s = ElasticURI("elastic://host:port")
      s.url should === ("host:port")
    }

    "index ('elastic://host:port/index') -> 'index'" in {
      val s = ElasticURI("elastic://host:port/index")
      s.index should === ("index")
    }
    "index ('elastic://host:port/index/') -> 'index'" in {
      val s = ElasticURI("elastic://host:port/index/")
      s.index should === ("index")
    }
    "index ('elastic://host:port/') -> ''" in {
      val s = ElasticURI("elastic://host:port/")
      s.index should === ("")
    }
  }
}
