package io.syspulse.skel.shop.item

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import io.jvm.uuid._

import io.syspulse.skel.shop.item.ItemGenerator

class ItemGeneratorSpec extends WordSpec with Matchers with ScalaFutures {
  
  "ItemGenerator" should {

    "generate 10 random items" in {
      val cc = ItemGenerator.random(10)
      cc.size should === (10)
    }

    "generate 1 item with data" in {
      val cc = ItemGenerator.random(1)
      cc(0).id should !== ("")
      cc(0).name should !== ("")
      assert(cc(0).count >= 0.0)
      assert(cc(0).count <= 1000.0)
      assert(cc(0).price >= 0.0)
      assert(cc(0).price <= 100.0)
    }
  }
}
