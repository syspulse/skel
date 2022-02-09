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

    """{ "user": {{user}}, "password": {{password}} } transform to {"user":"user0001","password":"pass0001"}""" in {
      val p = new ProxyM2MAuth("",
        Config(
          authBody = """ { "username": "{{user}}", "password": "{{pass}}" }""",
          authHeadersMapping = """ BODY:user:user0001,BODY:pass:pass0001 """
        )
      )  
      val b = p.transformBody()
      b should === (""" { "username": "user0001", "password": "pass0001" }""")
    }

    """HEADER:X-App-Id:{{USER}}, HEADER:X-App-Secret:{{HOME}} transform to EnvVars """ in {
      val p = new ProxyM2MAuth("",
        Config(
          authBody = "",
          authHeadersMapping = """ HEADER:X-App-Id:{{USER}}, HEADER:X-App-Secret:{{HOME}} """
        )
      )  
      val hh = p.transformHeaders()
      hh.size should === (2)
    }

    """HEADER:X-App-Id:{{os.name}}, HEADER:X-App-Secret:{{user.home}} transform to Properties """ in {
      val p = new ProxyM2MAuth("",
        Config(
          authBody = "",
          authHeadersMapping = """ HEADER:X-App-Id:{{os.name}}, HEADER:X-App-Secret:{{user.home}} """
        )
      )  
      val hh = p.transformHeaders()
      hh.size should === (2)
    }
    
  }
}
