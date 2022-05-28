package io.syspulse.skel.world.country

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import io.jvm.uuid._

import io.syspulse.skel.world.country.CountryLoader

class CountryLoaderSpec extends WordSpec with Matchers with ScalaFutures {
  
  
  "CountryLoader" should {

    "load all countries" in {
      val cc = CountryLoader.fromResource()
      cc.size should === (241)
    }

    "first country: Afghanistan" in {
      val cc = CountryLoader.fromResource()
      cc.head.name should === ("Afghanistan")
      cc.head.iso should === ("AF")
    }

    "last country: Zimbabwe" in {
      val cc = CountryLoader.fromResource()
      cc.last.name should === ("Zimbabwe")
      cc.last.iso should === ("ZW")
    }

    "load all countries with deterministic id" in {
      val cc1 = CountryLoader.fromResource()
      val cc2 = CountryLoader.fromResource()
      for(i <- 0 to cc1.size-1 ) {
        cc1(i).name should === (cc2(i).name)
        cc1(i).id should === (cc2(i).id)
      }

    }
  }
}
