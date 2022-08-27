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

  "KafkaUri" should {
    "broker ('kafka://host:port/topic') -> 'host:port'" in {
      val s = KafkaURI("kafka://host:port/topic")
      s.broker should === ("host:port")
    }

    "topic ('kafka://host:port/topic/group') -> 'topic'" in {
      val s = KafkaURI("kafka://host:port/topic/group")
      s.topic should === ("topic")
    }

    "group ('kafka://host:port/topic/group') -> 'group'" in {
      val s = KafkaURI("kafka://host:port/topic/group")
      s.group should === ("group")
    }

  }
}
