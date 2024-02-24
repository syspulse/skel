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

    "resovle with raw option ('kafka://host:port/topic?raw')" in {
      val s = KafkaURI("kafka://host:port/topic?raw")
      s.isRaw should === (true)
    }

    "resovle with json option ('kafka://host:port/topic?json')" in {
      val s = KafkaURI("kafka://host:port/topic?json")
      s.isRaw should === (false)
    }

    "resovle with default as raw option ('kafka://host:port/topic')" in {
      val s = KafkaURI("kafka://host:port/topic")
      s.isRaw should === (true)
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

  "JdbcURI" should {
    
    "parse 'postgres://host:5433/ingest_db'" in {
      val u = JdbcURI("postgres://host:5433/ingest_db")
      u.dbType should === ("postgres")
      u.db should === (Some("ingest_db"))
      u.dbConfig should === (None)
      u.host should === ("host")
      u.port should === (5433)
    }

    "parse 'postgres://user:pass@host:5433/ingest_db'" in {
      val u = JdbcURI("postgres://user:pass@host:5433/ingest_db")
      u.dbType should === ("postgres")
      u.db should === (Some("ingest_db"))
      u.dbConfig should === (None)
      u.host should === ("host")
      u.port should === (5433)
      
      u.user should === (Some("user"))
      u.pass should === (Some("pass"))
    }

    "parse 'mysql://host:5433/ingest_db'" in {
      val u = JdbcURI("mysql://host:5433/ingest_db")
      u.dbType should === ("mysql")
      u.db should === (Some("ingest_db"))
      u.dbConfig should === (None)
      u.host should === ("host")
      u.port should === (5433)
    }

    "parse 'jdbc://db1'" in {
      val u = JdbcURI("jdbc://db1")      
      u.dbType should === ("postgres")
      u.db should === (None)
      u.dbConfig should === (Some("db1"))
    }

    "parse 'jdbc:postgres://db1'" in {
      val u = JdbcURI("jdbc:postgres://db1")      
      u.dbType should === ("postgres")
      u.db should === (None)
      u.dbConfig should === (Some("db1"))
    }

    "parse 'jdbc://postgres://db1'" in {
      val u = JdbcURI("jdbc://postgres://db1")      
      u.dbType should === ("postgres")
      u.db should === (None)
      u.dbConfig should === (Some("db1"))
    }

    "parse 'postgres://db1'" in {
      val u = JdbcURI("postgres://db1")      
      u.dbType should === ("postgres")
      u.db should === (None)
      u.dbConfig should === (Some("db1"))
    }

    "parse 'mysql://db1'" in {
      val u = JdbcURI("mysql://db1")      
      u.dbType should === ("mysql")
      u.db should === (None)
      u.dbConfig should === (Some("db1"))
    }

    // --- Async ----------------------------
    "parse 'jdbc:async://db1'" in {
      val u = JdbcURI("jdbc:async://db1")      
      u.dbType should === ("postgres")
      u.db should === (None)
      u.async should === (true)
      u.dbConfig should === (Some("db1"))
    }

    "parse 'postgres:async://db1'" in {
      val u = JdbcURI("postgres:async://db1")      
      u.dbType should === ("postgres")
      u.db should === (None)
      u.async should === (true)
      u.dbConfig should === (Some("db1"))
    }

    "parse 'mysql:async://db1'" in {
      val u = JdbcURI("mysql:async://db1")      
      u.dbType should === ("mysql")
      u.db should === (None)
      u.async should === (true)
      u.dbConfig should === (Some("db1"))
    }
 
  }
}
