package io.syspulse.skel.world.currency

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import io.jvm.uuid._

import io.syspulse.skel.world.currency.CountryLoader

class CountryLoaderSpec extends WordSpec with Matchers with ScalaFutures {
  
  
  "CountryLoader" should {

    "load all countries" in {
      val cc = CountryLoader.fromResource()
      cc.size should === (241)
    }

    "first country: Afghanistan" in {
      val cc = CountryLoader.fromResource()
      cc.head.name should === ("Afghanistan")
      cc.head.short should === ("AF")
    }

    "last country: Zimbabwe" in {
      val cc = CountryLoader.fromResource()
      cc.last.name should === ("Zimbabwe")
      cc.last.short should === ("ZW")
    }
  }
}
