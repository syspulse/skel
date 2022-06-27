package io.syspulse.skel.util

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.Config
import io.syspulse.skel.auth.oauth2.ProxyM2MAuth
import io.syspulse.skel.auth.oauth2.ProxyTransformer
import akka.http.scaladsl.model.headers.RawHeader

case class DataUnit(v:Double,unit:String)
case class Data(ts:Long,v:DataUnit)
case class DataList(name:String,data:List[Data])

class ProxyM2MAuthSpec extends AnyWordSpec with Matchers {
  
  "ProxyM2MAuthSpec" should {

    //"HEADER:Content-type:application/json, HEADER:X-App-Id:{{client_id}}, HEADER:X-App-Secret:{{client_secret}}, BODY:X-User:{{user}}, BODY:X-Pass:{{pass}}"
    """ 'HEADER:Content-type:application/json' convert to the Transformer """ in {
      val p = new ProxyM2MAuth("",Config())
      val tt = p.getTransfomers(""" HEADER:Content-type:application/json """)

      tt.size should === (1)
      tt should === (Seq(ProxyTransformer("HEADER","Content-type","application/json")))
    }

    """ '"HEADER:Content-type:application/json, HEADER:X-App-Id:{{client_id}}, HEADER:X-App-Secret:{{client_secret}}, BODY:X-User:{{user}}, BODY:X-Pass:{{pass}}"' convert to the Transformer """ in {
      val p = new ProxyM2MAuth("",Config())
      val tt = p.getTransfomers(""" "HEADER:Content-type:application/json, HEADER:X-App-Id:{{client_id}}, HEADER:X-App-Secret:{{client_secret}}, BODY:X-User:{{user}}, BODY:X-Pass:{{pass}}" """)

      tt.size should === (5)
    }

    // """HEADER:X-App-Id:{{USER}}, HEADER:X-App-Secret:{{HOME}} transform to EnvVars """ in {
    //   val p = new ProxyM2MAuth("",
    //     Config(
    //       authBody = "",
    //       authHeadersMapping = """ HEADER:X-App-Id:{{USER}}, HEADER:X-App-Secret:{{HOME}} """
    //     )
    //   )  
    //   val hh = p.transformHeaders(Seq(RawHeader("X-App-Id","app1"),RawHeader("X-App-Secret","s1")))
    //   hh.size should === (2)
    // }

    // """HEADER:X-App-Id:{{os.name}}, HEADER:X-App-Secret:{{user.home}} transform to Properties """ in {
    //   val p = new ProxyM2MAuth("",
    //     Config(
    //       authBody = "",
    //       authHeadersMapping = """ HEADER:X-App-Id:{{os.name}}, HEADER:X-App-Secret:{{user.home}} """
    //     )
    //   )  
    //   val hh = p.transformHeaders(Seq(RawHeader("X-App-Id","app1"),RawHeader("X-App-Secret","s1")))
    //   hh.size should === (2)
    // }
    """HEADER:X-App-Id: app1, HEADER:X-App-Secret: secret1 transform to Pass through Headers """ in {
      val p = new ProxyM2MAuth("",
        Config(
          proxyBody = "",
          proxyHeadersMapping = """ HEADER:X-App-Id: "X-App-Id", HEADER:X-App-Secret: "X-App-Secret" """
        )
      )  
      val hh = p.transformHeaders(Seq(RawHeader("X-App-Id","app1"),RawHeader("X-App-Secret","s1")))
      hh.size should === (2)
    }

    """{ "user": {{user}}, "password": {{password}} } transform to {"user":"userXXXX","password":"passXXXX"}""" in {
      val p = new ProxyM2MAuth("",
        Config(
          proxyBody = """ { "username": "{{user}}", "password": "{{pass}}" }""",
          proxyHeadersMapping = """ BODY:X-User:user,BODY:X-Pass:pass """
        )
      )  
      val b1 = p.transformBody(Seq(RawHeader("X-User","user0001"),RawHeader("X-Pass","pass0001")))
      val b2 = p.transformBody(Seq(RawHeader("X-User","user0002"),RawHeader("X-Pass","pass0002")))
      b1 should === (""" { "username": "user0001", "password": "pass0001" }""")
      b2 should === (""" { "username": "user0002", "password": "pass0002" }""")

    }
    
  }
}
