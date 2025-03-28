package io.syspulse.skel.uri

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success


class KafkaUriSpec extends AnyWordSpec with Matchers {
  
  "KafkaURI" should {
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

    "resolve with raw option ('kafka://host:port/topic?raw')" in {
      val s = KafkaURI("kafka://host:port/topic?raw")
      s.isRaw should === (true)
    }

    "resolve with json option ('kafka://host:port/topic?json')" in {
      val s = KafkaURI("kafka://host:port/topic?json")
      s.isRaw should === (false)
    }

    "resolve with default as non-raw option ('kafka://host:port/topic')" in {
      val s = KafkaURI("kafka://host:port/topic")
      s.isRaw should === (false)
    }

    "resolve autocommit option" in {
      // by default autocommit is true
      KafkaURI("kafka://host:port/topic").autoCommit should === (true)
      KafkaURI("kafka://host:port/topic?autocommit").autoCommit should === (true)      
      KafkaURI("kafka://host:port/topic?autocommit=false").autoCommit should === (false)
    }

    "resolve with arbitrary options ('kafka://host:port/topic?key1=true&key2=value2')" in {
      val s = KafkaURI("kafka://host:port/topic?key1=true&key2=value2")
      s.ops should === (Map("key1" -> "true", "key2" -> "value2"))
    }

    "resolve with Kakfa Producer options ('kafka://host:port/topic?enable.idempotence=true')" in {
      val s = KafkaURI("kafka://host:port/topic?enable.idempotence=true")
      s.ops should === (Map("enable.idempotence" -> "true"))
    }

  }
  
}
