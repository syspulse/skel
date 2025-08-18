package io.syspulse.skel.util

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._

import scala.util.{Try,Success,Failure}
import java.time._
import scala.util.Random
import io.syspulse.skel.util.Util
// import io.syspulse.skel.util.Util

class UtilJsonSpec extends AnyWordSpec with Matchers {
  
  "UtilJson" should {
    val j0 = """
{
  "exp": 1710503139,
  "iat": 1707911139,
  "jti": "b1f9b191-711c-4005-ad79-d100ba73fee4",
  "iss": "",
  "sub": "026272c9-8b02-4aa2-ba92-89f90908788e",
  "typ": "Bearer",
  "azp": "system",
  "upn": "service-account",
  "clientHost": "109.108.74.188",
  "groups": [
    "default-roles-api",
    "user",
    "service-role"
  ],
  "client_id": "account-1"
}
      """

    "parse '.exp'" in {
      val r = Util.walkJson(j0,".exp")
      r.get.contains("1710503139") should ===(true)      
    }
    
    "parse '.groups[]'" in {
      val r = Util.walkJson(j0,".groups[]")
      r.get.size should ===(3)      
      r.get.contains("\"service-role\"") should ===(true)      
      r.get.contains("\"user\"") should ===(true)      
      r.get.contains("\"default-roles-api\"") should ===(true) 
    }

    "parse objects tree" in {
      val r = Util.walkJson("""{"data": {"role": "service"} }""",".data.role")
      r.get should ===(Seq("\"service\""))
    }

    "parse objects tree and not find" in {
      val r = Util.walkJson("""{"data": {"role": "service"} }""","data.role2")
      info(s"r = ${r}")
      r.isFailure should ===(true)
    }

    // This is not supported any more !
    "parse objects tree with array items" in {      
      val j2 = """{"data": {"groups": ["user","service"]} }"""
      Util.parseJson(j2,"data.groups[].service").get should === (Seq("service"))
      Util.parseJson(j2,"data.groups[].user").get should === (Seq("user"))
      Util.parseJson(j2,"data.groups[].UNKNWON").get should ===(Seq()) 
    }

        // This is to support old syntax and ext- dependecies !
    "parse JWT service role" in {
      val r = Util.parseJson(j0,"groups[].service-role")
      r.get.contains("service-role") should ===(true)      
    }
    

  }    
}
