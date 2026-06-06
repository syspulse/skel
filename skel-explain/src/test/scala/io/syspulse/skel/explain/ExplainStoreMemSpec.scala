package io.syspulse.skel.explain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import scala.util.{Success, Failure, Try}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import io.syspulse.skel.explain.store.{ExplainStore, ExplainStoreMem}

class ExplainStoreMemSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  val timeout = Duration(5, "seconds")

  var store: ExplainStoreMem = _

  override def beforeEach(): Unit = {
    store = new ExplainStoreMem()
  }

  "ExplainStoreMem" should {

    "store and retrieve a default rule (oid='')" in {
      val rule = Explain(oid = None, rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input")))
      Await.result(store.+(rule), timeout)

      Await.result(store.get(None, "DetectorWallet"), timeout) shouldBe rule
    }

    "store and retrieve a custom oid rule" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input.toUpperCase()")))
      Await.result(store.+(rule), timeout)

      Await.result(store.get(Some("490"), "DetectorWallet"), timeout) shouldBe rule
    }

    "return failure for non-existent rule" in {
      Try(Await.result(store.get(None, "NonExistent"), timeout)) shouldBe a[Failure[_]]
    }

    "store both default and custom oid rules for same rid independently" in {
      val defaultRule = Explain(oid = None, rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      val customRule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "input.toUpperCase()")))

      Await.result(store.+(defaultRule), timeout)
      Await.result(store.+(customRule), timeout)

      Await.result(store.get(None, "DetectorWallet"), timeout) shouldBe defaultRule
      Await.result(store.get(Some("490"), "DetectorWallet"), timeout) shouldBe customRule
    }

    "update a rule by storing with same oid/rid" in {
      val rule1 = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"v1\"")))
      val rule2 = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("js", "\"v2\"")))

      Await.result(store.+(rule1), timeout)
      Await.result(store.+(rule2), timeout)

      Await.result(store.get(Some("490"), "DetectorWallet"), timeout) shouldBe rule2
      Await.result(store.size, timeout) shouldBe 1
    }

    "delete a rule" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      Await.result(store.+(rule), timeout)

      Await.result(store.del(Some("490"), "DetectorWallet"), timeout)
      Try(Await.result(store.get(Some("490"), "DetectorWallet"), timeout)) shouldBe a[Failure[_]]
      Await.result(store.size, timeout) shouldBe 0
    }

    "fail to delete non-existent rule" in {
      Try(Await.result(store.del(None, "NonExistent"), timeout)) shouldBe a[Failure[_]]
    }

    "find all rules for a given oid" in {
      Await.result(store.+(Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("999"), rid = "Rule1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", "")))), timeout)

      val rules490 = Await.result(store.findByOid(Some("490")), timeout)
      rules490.size shouldBe 2
      rules490.map(_.rid).toSet shouldBe Set("Rule1", "Rule2")

      Await.result(store.findByOid(Some("999")), timeout).size shouldBe 1
      Await.result(store.findByOid(None), timeout).size shouldBe 1
    }

    "return all rules" in {
      Await.result(store.+(Explain(oid = None, rid = "Rule1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "Rule1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "Rule2", scripts = Seq(ExplainScript("str", "")))), timeout)

      Await.result(store.all, timeout).size shouldBe 3
      Await.result(store.size, timeout) shouldBe 3
    }

    "support ? lookup by composite key" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      Await.result(store.+(rule), timeout)

      Await.result(store.?("490_DetectorWallet"), timeout) shouldBe rule
    }

    "support del by composite key" in {
      val rule = Explain(oid = Some("490"), rid = "DetectorWallet", scripts = Seq(ExplainScript("str", "")))
      Await.result(store.+(rule), timeout)

      Await.result(store.del("490_DetectorWallet"), timeout)
      Await.result(store.size, timeout) shouldBe 0
    }

    "delByOid removes all rules for the given oid" in {
      Await.result(store.+(Explain(oid = Some("490"), rid = "R1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "R2", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("999"), rid = "R1", scripts = Seq(ExplainScript("str", "")))), timeout)

      val deleted = Await.result(store.delByOid(Some("490")), timeout)
      deleted.size shouldBe 2
      deleted.forall(_.oid == Some("490")) shouldBe true

      Await.result(store.findByOid(Some("490")), timeout) shouldBe empty
      Await.result(store.findByOid(Some("999")), timeout).size shouldBe 1
    }

    "delByOid returns empty list when oid has no rules" in {
      val deleted = Await.result(store.delByOid(Some("no-such-oid")), timeout)
      deleted shouldBe empty
    }

    "store and retrieve a script whose src contains double quotes" in {
      val src = """var s = "hello \"world\""; s"""
      val rule = Explain(oid = None, rid = "QuoteRule", scripts = Seq(ExplainScript("js", src)))
      Await.result(store.+(rule), timeout)

      val r = Await.result(store.get(None, "QuoteRule"), timeout)
      r.scripts.head.src shouldBe src
    }

    "list rules filtered by oid and rid" in {
      Await.result(store.+(Explain(oid = Some("490"), rid = "R1", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "R2", scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("999"), rid = "R1", scripts = Seq(ExplainScript("str", "")))), timeout)

      Await.result(store.list(oid = Some("490")), timeout).rules.map(_.rid).toSet shouldBe Set("R1", "R2")
      Await.result(store.list(oid = Some("490"), rid = Some("R1")), timeout).rules.map(_.rid) shouldBe Seq("R1")
      Await.result(store.list(oid = Some("490"), from = Some(0), size = Some(1)), timeout).rules.size shouldBe 1
    }

    "page rules with from and size" in {
      (1 to 5).foreach { i =>
        Await.result(store.+(Explain(oid = None, rid = s"R$i", scripts = Seq(ExplainScript("str", "")))), timeout)
      }

      Await.result(store.???(1, 2), timeout).size shouldBe 2
      Await.result(store.???(3, 2), timeout).size shouldBe 2
      Await.result(store.???(10, 2), timeout) shouldBe empty
      Await.result(store.???(-1, 1), timeout).size shouldBe 1
      Await.result(store.???(0, 0), timeout) shouldBe empty
      Await.result(store.all, timeout).size shouldBe 5
    }

    "search rules case-insensitively with minimum query length" in {
      Await.result(store.+(Explain(oid = None, rid = "R1", name = Some("Needle Rule"), scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = Some("490"), rid = "R2", desc = Some("wallet-needle-desc"), scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = None, rid = "R3", name = Some("Other"), scripts = Seq(ExplainScript("str", "")))), timeout)

      Await.result(store.search("NEEDLE"), timeout).rules.map(_.rid).toSet shouldBe Set("R1", "R2")
      Await.result(store.search("ne"), timeout).rules shouldBe empty
    }

    "search rules by prefix, middle and postfix in name" in {
      Await.result(store.+(Explain(oid = None, rid = "R1", name = Some("prefixAlphabetapostfix"), scripts = Seq(ExplainScript("str", "")))), timeout)

      Await.result(store.search("pre"), timeout).rules.map(_.rid) shouldBe Seq("R1")
      Await.result(store.search("pha"), timeout).rules.map(_.rid) shouldBe Seq("R1")
      Await.result(store.search("fix"), timeout).rules.map(_.rid) shouldBe Seq("R1")
    }

    "search rules by prefix, middle and postfix in description" in {
      Await.result(store.+(Explain(oid = None, rid = "R1", desc = Some("prefixAlphabetapostfix"), scripts = Seq(ExplainScript("str", "")))), timeout)

      Await.result(store.search("pre"), timeout).rules.map(_.rid) shouldBe Seq("R1")
      Await.result(store.search("pha"), timeout).rules.map(_.rid) shouldBe Seq("R1")
      Await.result(store.search("fix"), timeout).rules.map(_.rid) shouldBe Seq("R1")
    }

    "build postgres prefix tsquery" in {
      ExplainStore.postgresPrefixTsQuery("pre") shouldBe Some("pre:*")
      ExplainStore.postgresPrefixTsQuery("prefix Alphabet") shouldBe Some("prefix:* & alphabet:*")
      ExplainStore.postgresPrefixTsQuery("  ") shouldBe None
    }

    "build postgres search where with tgram for substring match" in {
      import io.syspulse.skel.store.{StoreFts, StoreSearch}
      def sqlLit(s: String): String = s.replace("'", "''")
      val where = StoreFts.postgresSearchWhere(
        Set(StoreSearch.Tgram),
        None,
        Seq("name", "description"),
        "Wallet",
        sqlLit,
      ).get
      where should include("LIKE '%wallet%'")
    }

    "tokenize camelCase names for postgres FTS" in {
      ExplainStore.tokenizeSearchField("DetectorWallet Updated") should contain allOf ("detector", "wallet", "updated")
      ExplainStore.postgresPrefixTsQuery("Wallet") shouldBe Some("wallet:*")
      ExplainStore.postgresPrefixTsQuery("DetectorWallet") shouldBe Some("detector:* & wallet:*")
    }

    "search rules by regexp substring in name and description" in {
      Await.result(store.+(Explain(oid = None, rid = "R1", name = Some("demo-rule-name"), scripts = Seq(ExplainScript("str", "")))), timeout)
      Await.result(store.+(Explain(oid = None, rid = "R2", desc = Some("needle in description"), scripts = Seq(ExplainScript("str", "")))), timeout)

      Await.result(store.search("demo"), timeout).rules.map(_.rid) shouldBe Seq("R1")
      Await.result(store.search("eedle"), timeout).rules.map(_.rid) shouldBe Seq("R2")
    }

    "page search results with from and size" in {
      (1 to 5).foreach { i =>
        Await.result(store.+(Explain(oid = None, rid = s"R$i", name = Some(s"pager-name-$i"), scripts = Seq(ExplainScript("str", "")))), timeout)
      }

      val p0 = Await.result(store.search("pager", Some(0), Some(2)), timeout)
      p0.rules.size shouldBe 2
      p0.total shouldBe 5
      val p2 = Await.result(store.search("pager", Some(2), Some(2)), timeout)
      p2.rules.size shouldBe 2
      p2.total shouldBe 5
      Await.result(store.search("pager", Some(10), Some(2)), timeout).rules shouldBe empty
    }

    "store and retrieve multiple scripts each with quotes in src" in {
      val src1 = """"prefix: \"value\"""""
      val src2 = """"suffix: \" + input + \""""
      val rule = Explain(oid = None, rid = "MultiQuoteRule", scripts = Seq(
        ExplainScript("js", src1),
        ExplainScript("js", src2)
      ))
      Await.result(store.+(rule), timeout)

      val r = Await.result(store.get(None, "MultiQuoteRule"), timeout)
      r.scripts(0).src shouldBe src1
      r.scripts(1).src shouldBe src2
    }
  }
}
