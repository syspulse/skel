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

    "parse 'jdbc://postgres'" in {
      val u = JdbcURI("jdbc://postgres")      
      u.dbType should === ("postgres")
      u.db should === (None)
      u.dbConfig should === (Some("postgres"))
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

  "HttpURI" should {
    "parse 'http://POST@123456789@host:8080/url'" in {
      val u = HttpURI("http://POST@123456789@host:8080/url")
      u.rest should === ("host:8080/url")
      u.verb should === ("POST")
      u.async should === (false)
      u.headers should === (Map("Authorization" -> s"Bearer 123456789"))
    }
    
    "parse 'http://POST@host:8080/url' with default Auth" in {
      val u = HttpURI("http://POST@host:8080/url",Some("123456789"))
      u.rest should === ("host:8080/url")
      u.verb should === ("POST")
      u.async should === (false)
      u.headers should === (Map("Authorization" -> s"Bearer 123456789"))
    }
  }

  "AkkaURI" should {
    "parse 'akka://system1/actor1'" in {
      val u = AkkaURI("akka://system1/actor1")
      u.system should === ("system1")
      u.actor should === ("actor1")      
    }

    "parse 'akka://system1@localhost:3333/actor1'" in {
      val u = AkkaURI("akka://system1@localhost:3333/actor1")
      u.system should === ("system1")
      u.actor should === ("actor1")
      u.host should === (Some("localhost"))
      u.port should === (Some(3333))
    }
    
  }
}
