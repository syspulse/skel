package io.syspulse.skel.tls

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import scala.util.Success

class SslSpec extends AnyWordSpec with Matchers {
  
  "SslSpec" should {    

    "resolve SSL 'google.com:443' as valid and trusted" in {
      //val ssl = new SSLCertificateExtractor("object.ecstestdrive.com:443")
      val ssl = SslUtil.resolve("google.com:443")      
      
      ssl.isFailure === (false)
      ssl.get.valid === (true)
      ssl.get.trusted === (true)
      ssl.get.expire > (Instant.now().toEpochMilli)
    }

    "resolve SSL 'HTTPS://google.com' as valid and trusted" in {
      //val ssl = new SSLCertificateExtractor("object.ecstestdrive.com:443")
      val ssl = SslUtil.resolve("HTTPS://google.com")
      
      ssl.isFailure === (false)
      ssl.get.valid === (true)
      ssl.get.trusted === (true)
      ssl.get.expire > (Instant.now().toEpochMilli)
    }

    "resolve SSL 'admin-extractor.hacken.dev' as valid and not trusted" in {
      val ssl = SslUtil.resolve("admin-extractor.hacken.dev")      

      //info(s"result = ${ssl}")

      ssl.isFailure === (false)
      ssl.get.valid === (true)
      ssl.get.trusted === (false)
      ssl.get.expire > (Instant.now().toEpochMilli)
    }

    "resolve SSL 'HTTPS://google.com/' as valid and trusted" in {
      //val ssl = new SSLCertificateExtractor("object.ecstestdrive.com:443")
      val ssl = SslUtil.resolve("HTTPS://google.com/")
      
      ssl.isFailure === (false)
      ssl.get.valid === (true)
      ssl.get.trusted === (true)
      ssl.get.expire > (Instant.now().toEpochMilli)
    }    

    "resolve SSL 'HTTPS://google.com/123/' as valid and trusted" in {
      val ssl = SslUtil.resolve("HTTPS://google.com/123/")
      
      ssl.isFailure === (false)
      ssl.get.valid === (true)
      ssl.get.trusted === (true)
      ssl.get.expire > (Instant.now().toEpochMilli)
    }    
  }
}
