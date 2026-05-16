package io.syspulse.skel.explain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import scala.util.{Success, Failure}

import io.syspulse.skel.explain.store.ExplainStoreMem

class ExplainStoreMemSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  var store: ExplainStoreMem = _

  override def beforeEach(): Unit = {
    store = new ExplainStoreMem()
  }

  "ExplainStoreMem" should {

    "store and retrieve a default rule (oid='')" in {
      val rule = ExplainRule(oid = "", rid = "DetectorWallet", scripts = Seq("js://input"))
      store.+(rule) shouldBe a[Success[_]]

      store.get("", "DetectorWallet") shouldBe Success(rule)
    }

    "store and retrieve a custom oid rule" in {
      val rule = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("js://input.toUpperCase()"))
      store.+(rule) shouldBe a[Success[_]]

      store.get("490", "DetectorWallet") shouldBe Success(rule)
    }

    "return failure for non-existent rule" in {
      store.get("", "NonExistent") shouldBe a[Failure[_]]
    }

    "store both default and custom oid rules for same rid independently" in {
      val defaultRule = ExplainRule(oid = "", rid = "DetectorWallet", scripts = Seq("str://"))
      val customRule = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("js://input.toUpperCase()"))

      store.+(defaultRule)
      store.+(customRule)

      store.get("", "DetectorWallet") shouldBe Success(defaultRule)
      store.get("490", "DetectorWallet") shouldBe Success(customRule)
    }

    "update a rule by storing with same oid/rid" in {
      val rule1 = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("js://\"v1\""))
      val rule2 = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("js://\"v2\""))

      store.+(rule1)
      store.+(rule2)

      store.get("490", "DetectorWallet") shouldBe Success(rule2)
      store.size shouldBe 1
    }

    "delete a rule" in {
      val rule = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("str://"))
      store.+(rule)

      store.del("490", "DetectorWallet") shouldBe a[Success[_]]
      store.get("490", "DetectorWallet") shouldBe a[Failure[_]]
      store.size shouldBe 0
    }

    "fail to delete non-existent rule" in {
      store.del("", "NonExistent") shouldBe a[Failure[_]]
    }

    "find all rules for a given oid" in {
      store.+(ExplainRule(oid = "490", rid = "Rule1", scripts = Seq("str://")))
      store.+(ExplainRule(oid = "490", rid = "Rule2", scripts = Seq("str://")))
      store.+(ExplainRule(oid = "999", rid = "Rule1", scripts = Seq("str://")))
      store.+(ExplainRule(oid = "", rid = "Rule1", scripts = Seq("str://")))

      val rules490 = store.findByOid("490")
      rules490.size shouldBe 2
      rules490.map(_.rid).toSet shouldBe Set("Rule1", "Rule2")

      store.findByOid("999").size shouldBe 1
      store.findByOid("").size shouldBe 1
    }

    "return all rules" in {
      store.+(ExplainRule(oid = "", rid = "Rule1", scripts = Seq("str://")))
      store.+(ExplainRule(oid = "490", rid = "Rule1", scripts = Seq("str://")))
      store.+(ExplainRule(oid = "490", rid = "Rule2", scripts = Seq("str://")))

      store.all.size shouldBe 3
      store.size shouldBe 3
    }

    "support ? lookup by composite key" in {
      val rule = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("str://"))
      store.+(rule)

      store.?("490__DetectorWallet") shouldBe Success(rule)
    }

    "support del by composite key" in {
      val rule = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("str://"))
      store.+(rule)

      store.del("490__DetectorWallet") shouldBe a[Success[_]]
      store.size shouldBe 0
    }
  }
}
