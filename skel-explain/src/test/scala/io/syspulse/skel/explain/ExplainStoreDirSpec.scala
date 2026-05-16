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
      val rule = ExplainRule(oid = "", rid = "DetectorWallet", scripts = Seq("str://"), name = Some("Default"))
      store.+(rule) shouldBe a[Success[_]]

      store.get("", "DetectorWallet") shouldBe Success(rule)
    }

    "persist and reload a custom oid rule" in {
      val rule = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("js://input.toUpperCase()"))
      store.+(rule) shouldBe a[Success[_]]

      store.get("490", "DetectorWallet") shouldBe Success(rule)
    }

    "persist multiple rules and retrieve them all" in {
      val r1 = ExplainRule(oid = "", rid = "Rule1", scripts = Seq("str://"))
      val r2 = ExplainRule(oid = "490", rid = "Rule1", scripts = Seq("str://"))
      val r3 = ExplainRule(oid = "490", rid = "Rule2", scripts = Seq("str://"))

      store.+(r1); store.+(r2); store.+(r3)

      store.size shouldBe 3
      store.all.size shouldBe 3
    }

    "delete a rule and verify it is gone" in {
      val rule = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("str://"))
      store.+(rule)

      store.del("490", "DetectorWallet") shouldBe a[Success[_]]
      store.get("490", "DetectorWallet") shouldBe a[Failure[_]]
      store.size shouldBe 0
    }

    "reload rules from directory on new store instance" in {
      val rule1 = ExplainRule(oid = "", rid = "DetectorWallet", scripts = Seq("js://\"default\""))
      val rule2 = ExplainRule(oid = "490", rid = "DetectorWallet", scripts = Seq("js://\"custom\""))
      store.+(rule1)
      store.+(rule2)

      val store2 = new ExplainStoreDir(tempDir.toString)
      store2.size shouldBe 2
      store2.get("", "DetectorWallet").isSuccess shouldBe true
      store2.get("490", "DetectorWallet").isSuccess shouldBe true
    }

    "find rules by oid" in {
      store.+(ExplainRule(oid = "490", rid = "Rule1", scripts = Seq("str://")))
      store.+(ExplainRule(oid = "490", rid = "Rule2", scripts = Seq("str://")))
      store.+(ExplainRule(oid = "", rid = "Rule1", scripts = Seq("str://")))

      store.findByOid("490").size shouldBe 2
      store.findByOid("").size shouldBe 1
    }

    "persist a ScriptFlow with multiple scripts" in {
      val rule = ExplainRule(
        oid = "",
        rid = "MultiScript",
        scripts = Seq("js://JSON.parse(input).balance.toString()", "js://\"Balance: \" + input")
      )
      store.+(rule)

      val store2 = new ExplainStoreDir(tempDir.toString)
      val loaded = store2.get("", "MultiScript")
      loaded.isSuccess shouldBe true
      loaded.get.scripts.size shouldBe 2
    }
  }
}
