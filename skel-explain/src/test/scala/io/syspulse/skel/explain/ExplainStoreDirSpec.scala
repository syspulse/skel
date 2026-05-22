package io.syspulse.skel.explain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import scala.util.{Success, Failure}
import java.nio.file.Files

import io.syspulse.skel.explain.store.ExplainStoreDir

class ExplainStoreDirSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  var tempDir: java.nio.file.Path = _
  var store: ExplainStoreDir = _

  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("explain-store-test")
    store = new ExplainStoreDir(tempDir.toString)
  }

  override def afterEach(): Unit = {
    os.remove.all(os.Path(tempDir.toString))
  }

  "ExplainStoreDir" should {

    "persist and reload a default rule" in {
      val rule = Explain(oid = None, rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")), name = Some("Default"))
      store.+(rule) shouldBe a[Success[_]]

      store.get(None, "DetectorWallet") shouldBe Success(rule)
    }

    "persist and reload a custom oid rule" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input.toUpperCase()")))
      store.+(rule) shouldBe a[Success[_]]

      store.get(Some("490"), "DetectorWallet") shouldBe Success(rule)
    }

    "persist multiple rules and retrieve them all" in {
      val r1 = Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", "")))
      val r2 = Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", "")))
      val r3 = Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", "")))

      store.+(r1); store.+(r2); store.+(r3)

      store.size shouldBe 3
      store.all.size shouldBe 3
    }

    "delete a rule and verify it is gone" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      store.+(rule)

      store.del(Some("490"), "DetectorWallet") shouldBe a[Success[_]]
      store.get(Some("490"), "DetectorWallet") shouldBe a[Failure[_]]
      store.size shouldBe 0
    }

    "reload rules from directory on new store instance" in {
      val rule1 = Explain(oid = None, rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"default\"")))
      val rule2 = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"custom\"")))
      store.+(rule1)
      store.+(rule2)

      val store2 = new ExplainStoreDir(tempDir.toString)
      store2.size shouldBe 2
      store2.get(None, "DetectorWallet").isSuccess shouldBe true
      store2.get(Some("490"), "DetectorWallet").isSuccess shouldBe true
    }

    "find rules by oid" in {
      store.+(Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", ""))))

      store.findByOid(Some("490")).size shouldBe 2
      store.findByOid(None).size shouldBe 1
    }

    "persist a ScriptFlow with multiple scripts" in {
      val rule = Explain(
        oid = None,
        rid = "MultiScript",
        scripts = Seq(
          ExplainScript("js", "JSON.parse(input).balance.toString()"),
          ExplainScript("js", "\"Balance: \" + input")
        )
      )
      store.+(rule)

      val store2 = new ExplainStoreDir(tempDir.toString)
      val loaded = store2.get(None, "MultiScript")
      loaded.isSuccess shouldBe true
      loaded.get.scripts.size shouldBe 2
    }

    "delByOid removes rules from memory and disk" in {
      store.+(Explain(oid = Some("490"), rid = "D1", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = Some("490"), rid = "D2", scripts = Seq(ExplainScript("str", ""))))
      store.+(Explain(oid = None, rid = "D1", scripts = Seq(ExplainScript("str", ""))))

      val deleted = store.delByOid(Some("490"))
      deleted.isSuccess shouldBe true
      deleted.get.size shouldBe 2

      // new instance reads from disk — 490 rules must be gone
      val store2 = new ExplainStoreDir(tempDir.toString)
      store2.findByOid(Some("490")) shouldBe empty
      store2.findByOid(None).size shouldBe 1
    }

    "persist and reload a script whose src contains double quotes" in {
      val src = """var s = "This is \"quoted\" text"; s"""
      val rule = Explain(oid = None, rid = "QuoteRule", scripts = Seq(ExplainScript("js", src)))
      store.+(rule) shouldBe a[Success[_]]

      val store2 = new ExplainStoreDir(tempDir.toString)
      val loaded = store2.get(None, "QuoteRule")
      loaded.isSuccess shouldBe true
      loaded.get.scripts.head.src shouldBe src
    }

    "persist and reload a script with quotes in opts field" in {
      val src  = """Explain: "${input}" in detail"""
      val opts = """openai://gpt-4o"""
      val rule = Explain(oid = None, rid = "QuoteOptsRule", scripts = Seq(ExplainScript("ai", src, Some(opts))))
      store.+(rule) shouldBe a[Success[_]]

      val store2 = new ExplainStoreDir(tempDir.toString)
      val loaded = store2.get(None, "QuoteOptsRule")
      loaded.isSuccess shouldBe true
      loaded.get.scripts.head.src  shouldBe src
      loaded.get.scripts.head.opts shouldBe Some(opts)
    }
  }
}
