package io.syspulse.skel.world.currency

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import io.jvm.uuid._

import io.syspulse.skel.world.currency.CurrencyLoader

class CurrencyLoaderSpec extends WordSpec with Matchers with ScalaFutures {
  
  "CurrencyLoader" should {

    "load all currencies" in {
      val cc = CurrencyLoader.fromResource()
      cc.size should === (441)
    }

    "first currency: AFN" in {
      val cc = CurrencyLoader.fromResource()
      cc.head.name should === ("Afghani")
      cc.head.code should === ("AFN")
    }

    "last currency: XFU" in {
      val cc = CurrencyLoader.fromResource()
      cc.last.name should === ("UIC-Franc")
      cc.last.code should === ("XFU")
    }
  }
}
