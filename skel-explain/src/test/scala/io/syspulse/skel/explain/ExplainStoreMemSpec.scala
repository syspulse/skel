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
      val rule = Explain(oid = None, rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input")))
      store.+(rule) shouldBe a[Success[_]]

      store.get(None, "DetectorWallet") shouldBe Success(rule)
    }

    "store and retrieve a custom oid rule" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input.toUpperCase()")))
      store.+(rule) shouldBe a[Success[_]]

      store.get(Some("490"), "DetectorWallet") shouldBe Success(rule)
    }

    "return failure for non-existent rule" in {
      store.get(None, "NonExistent") shouldBe a[Failure[_]]
    }

    "store both default and custom oid rules for same rid independently" in {
      val defaultRule = Explain(oid = None, rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      val customRule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input.toUpperCase()")))

      store.+(defaultRule)
      store.+(customRule)

      store.get(None, "DetectorWallet") shouldBe Success(defaultRule)
      store.get(Some("490"), "DetectorWallet") shouldBe Success(customRule)
    }

    "update a rule by storing with same oid/rid" in {
      val rule1 = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"v1\"")))
      val rule2 = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"v2\"")))

      store.+(rule1)
      store.+(rule2)

      store.get(Some("490"), "DetectorWallet") shouldBe Success(rule2)
      store.size shouldBe 1
    }

    "delete a rule" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      store.+(rule)

      store.del(Some("490"), "DetectorWallet") shouldBe a[Success[_]]
      store.get(Some("490"), "DetectorWallet") shouldBe a[Failure[_]]
      store.size shouldBe 0
    }

    "fail to delete non-existent rule" in {
      store.del(None, "NonExistent") shouldBe a[Failure[_]]
    }

    "find all rules for a given oid" in {
      store.+(Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("999"), rid = "Rule1", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", ""))))

      val rules490 = store.findByOid(Some("490"))
      rules490.size shouldBe 2
      rules490.map(_.rid).toSet shouldBe Set("Rule1", "Rule2")

      store.findByOid(Some("999")).size shouldBe 1
      store.findByOid(None).size shouldBe 1
    }

    "return all rules" in {
      store.+(Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", ""))))

      store.all.size shouldBe 3
      store.size shouldBe 3
    }

    "support ? lookup by composite key" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      store.+(rule)

      store.?("490_DetectorWallet") shouldBe Success(rule)
    }

    "support del by composite key" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      store.+(rule)

      store.del("490_DetectorWallet") shouldBe a[Success[_]]
      store.size shouldBe 0
    }

    "delByOid removes all rules for the given oid" in {
      store.+(Explain(oid = Some("490"), rid = "R1", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("490"), rid = "R2", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("999"), rid = "R1", scripts = Seq(ExplainScript("str", ""))))

      val deleted = store.delByOid(Some("490"))
      deleted.isSuccess shouldBe true
      deleted.get.size shouldBe 2
      deleted.get.forall(_.oid == Some("490")) shouldBe true

      store.findByOid(Some("490")) shouldBe empty
      store.findByOid(Some("999")).size shouldBe 1
    }

    "delByOid returns empty list when oid has no rules" in {
      val deleted = store.delByOid(Some("no-such-oid"))
      deleted.isSuccess shouldBe true
      deleted.get shouldBe empty
    }

    "store and retrieve a script whose src contains double quotes" in {
      val src = """var s = "hello \"world\""; s"""
      val rule = Explain(oid = None, rid = "QuoteRule", scripts = Seq(ExplainScript("js", src)))
      store.+(rule) shouldBe a[Success[_]]

      val r = store.get(None, "QuoteRule")
      r.isSuccess shouldBe true
      r.get.scripts.head.src shouldBe src
    }

    "store and retrieve multiple scripts each with quotes in src" in {
      val src1 = """"prefix: \"value\"""""
      val src2 = """"suffix: \" + input + \""""
      val rule = Explain(oid = None, rid = "MultiQuoteRule", scripts = Seq(
        ExplainScript("js", src1),
        ExplainScript("js", src2)
      ))
      store.+(rule) shouldBe a[Success[_]]

      val r = store.get(None, "MultiQuoteRule")
      r.isSuccess shouldBe true
      r.get.scripts(0).src shouldBe src1
      r.get.scripts(1).src shouldBe src2
    }
  }
}
