package io.syspulse.skel.uri

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success

import io.syspulse.skel.uri.ElasticURI
class UriSpec extends AnyWordSpec with Matchers {
  
  "ElasticURI" should {
    "url ('elastic://host:port/index') -> 'http://host:port'" in {
      val s = ElasticURI("elastic://host:port/index")
      s.url should === ("http://host:port")
    }

    "url ('elastic://host:port/') -> 'http://host:port'" in {
      val s = ElasticURI("elastic://host:port/")
      s.url should === ("http://host:port")
    }

    "url ('elastic://host:port') -> 'http://host:port'" in {
      val s = ElasticURI("elastic://host:port")
      s.url should === ("http://host:port")
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

  }

  "ParqURI" should {
    "'parq:///mnt/s3/file-{HH}.parq' -> '/mnt/s3/file-{HH}.parq' with no compressions" in {
      val u = ParqURI("parq:///mnt/s3/file-{HH}.parq")
      u.file should === ("/mnt/s3/file-{HH}.parq")
      u.zip should === ("parq")
      u.mode should === ("OVERWRITE")
    }

    "'/mnt/s3/file-{HH}.parq' -> '/mnt/s3/file-{HH}.parq' with no compressions" in {
      val u = ParqURI("parq:///mnt/s3/file-{HH}.parq")
      u.file should === ("/mnt/s3/file-{HH}.parq")
      u.zip should === ("parq")
      u.mode should === ("OVERWRITE")
    }

    "'parq://APPEND:/mnt/s3/file-{HH}.parq' -> '/mnt/s3/file-{HH}.parq' with no compressions" in {
      val u = ParqURI("parq://APPEND:/mnt/s3/file-{HH}.parq")
      u.file should === ("/mnt/s3/file-{HH}.parq")
      u.zip should === ("parq")
      u.mode should === ("APPEND")
    }

    "'parq:///mnt/s3/file-{HH}.parq.snappy' -> '/mnt/s3/file-{HH}.parq' with no compressions" in {
      val u = ParqURI("parq:///mnt/s3/file-{HH}.parq.snappy")
      u.file should === ("/mnt/s3/file-{HH}.parq.snappy")
      u.zip should === ("snappy")
      u.mode should === ("OVERWRITE")
    }
 
  }
}
