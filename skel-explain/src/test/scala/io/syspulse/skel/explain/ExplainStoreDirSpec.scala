package io.syspulse.skel.explain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import scala.util.{Success, Failure, Try}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.file.Files

import io.syspulse.skel.explain.store.ExplainStoreDir

class ExplainStoreDirSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  val timeout = Duration(5, "seconds")

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
      Await.result(store.+(rule), timeout)

      Await.result(store.get(None, "DetectorWallet"), timeout) shouldBe rule
    }

    "persist and reload a custom oid rule" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input.toUpperCase()")))
      Await.result(store.+(rule), timeout)

      Await.result(store.get(Some("490"), "DetectorWallet"), timeout) shouldBe rule
    }

    "persist multiple rules and retrieve them all" in {
      val r1 = Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", "")))
      val r2 = Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", "")))
      val r3 = Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", "")))

      Await.result(store.+(r1), timeout)
      Await.result(store.+(r2), timeout)
      Await.result(store.+(r3), timeout)

      Await.result(store.size, timeout) shouldBe 3
      Await.result(store.all, timeout).size shouldBe 3
    }

    "delete a rule and verify it is gone" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      Await.result(store.+(rule), timeout)

      Await.result(store.del(Some("490"), "DetectorWallet"), timeout)
      Try(Await.result(store.get(Some("490"), "DetectorWallet"), timeout)) shouldBe a[Failure[_]]
      Await.result(store.size, timeout) shouldBe 0
    }

    "reload rules from directory on new store instance" in {
      val rule1 = Explain(oid = None, rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"default\"")))
      val rule2 = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"custom\"")))
      Await.result(store.+(rule1), timeout)
      Await.result(store.+(rule2), timeout)

      val store2 = new ExplainStoreDir(tempDir.toString)
      Await.result(store2.size, timeout) shouldBe 2
      Try(Await.result(store2.get(None, "DetectorWallet"), timeout)).isSuccess shouldBe true
      Try(Await.result(store2.get(Some("490"), "DetectorWallet"), timeout)).isSuccess shouldBe true
    }

    "find rules by oid" in {
      Await.result(store.+(Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", "")))), timeout)

      Await.result(store.findByOid(Some("490")), timeout).size shouldBe 2
      Await.result(store.findByOid(None), timeout).size shouldBe 1
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
      Await.result(store.+(rule), timeout)

      val store2 = new ExplainStoreDir(tempDir.toString)
      val loaded = Await.result(store2.get(None, "MultiScript"), timeout)
      loaded.scripts.size shouldBe 2
    }

    "delByOid removes rules from memory and disk" in {
      Await.result(store.+(Explain(oid = Some("490"), rid = "D1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "D2", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = None, rid = "D1", scripts = Seq(ExplainScript("str", "")))), timeout)

      val deleted = Await.result(store.delByOid(Some("490")), timeout)
      deleted.size shouldBe 2

      // new instance reads from disk — 490 rules must be gone
      val store2 = new ExplainStoreDir(tempDir.toString)
      Await.result(store2.findByOid(Some("490")), timeout) shouldBe empty
      Await.result(store2.findByOid(None), timeout).size shouldBe 1
    }

    "persist and reload a script whose src contains double quotes" in {
      val src = """var s = "This is \"quoted\" text"; s"""
      val rule = Explain(oid = None, rid = "QuoteRule", scripts = Seq(ExplainScript("js", src)))
      Await.result(store.+(rule), timeout)

      val store2 = new ExplainStoreDir(tempDir.toString)
      val loaded = Await.result(store2.get(None, "QuoteRule"), timeout)
      loaded.scripts.head.src shouldBe src
    }

    "persist and reload a script with quotes in opts field" in {
      val src  = """Explain: "${input}" in detail"""
      val opts = """openai://gpt-4o"""
      val rule = Explain(oid = None, rid = "QuoteOptsRule", scripts = Seq(ExplainScript("ai", src, Some(opts))))
      Await.result(store.+(rule), timeout)

      val store2 = new ExplainStoreDir(tempDir.toString)
      val loaded = Await.result(store2.get(None, "QuoteOptsRule"), timeout)
      loaded.scripts.head.src  shouldBe src
      loaded.scripts.head.opts shouldBe Some(opts)
    }
  }
}
