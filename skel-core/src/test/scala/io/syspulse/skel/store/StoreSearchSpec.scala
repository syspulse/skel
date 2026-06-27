package io.syspulse.skel.store

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import io.syspulse.skel.uri.JdbcURI

class StoreSearchSpec extends AnyWordSpec with Matchers {

  "StoreSearch" should {
    "parse search index kinds" in {
      StoreSearch.parse("fts") shouldBe Set(StoreSearch.Fts)
      StoreSearch.parse("tgram") shouldBe Set(StoreSearch.Tgram)
      StoreSearch.parse("fts,tgram") shouldBe Set(StoreSearch.Fts, StoreSearch.Tgram)
      StoreSearch.parse("fts+tgram") shouldBe Set(StoreSearch.Fts, StoreSearch.Tgram)
      StoreSearch.parse("") shouldBe empty
    }

    "prefer URI search param over constructor default" in {
      val uri = JdbcURI("jdbc://postgres?search=tgram")
      StoreSearch.resolve(uri, Some(Set(StoreSearch.Fts))) shouldBe Set(StoreSearch.Tgram)
    }

    "use constructor when URI has no search param" in {
      val uri = JdbcURI("jdbc://postgres")
      StoreSearch.resolve(uri, Some(Set(StoreSearch.Tgram))) shouldBe Set(StoreSearch.Tgram)
    }

    "default to fts when unset" in {
      val uri = JdbcURI("jdbc://postgres")
      StoreSearch.resolve(uri, None) shouldBe Set(StoreSearch.Fts)
    }
  }

  "StoreFts.postgresSearchWhere" should {
    def sqlLit(s: String): String = s.replace("'", "''")

    "build fts-only where clause" in {
      StoreFts.postgresSearchWhere(
        Set(StoreSearch.Fts),
        Some("tsv"),
        Seq("name"),
        "wallet",
        sqlLit,
      ) shouldBe Some("(tsv @@ to_tsquery('simple', 'wallet:*'))")
    }

    "build tgram-only where clause" in {
      StoreFts.postgresSearchWhere(
        Set(StoreSearch.Tgram),
        None,
        Seq("name", "description"),
        "Wallet",
        sqlLit,
      ) shouldBe Some("(lower(coalesce(name, '')) LIKE '%wallet%' OR lower(coalesce(description, '')) LIKE '%wallet%')")
    }

    "combine fts and tgram with OR" in {
      val where = StoreFts.postgresSearchWhere(
        Set(StoreSearch.Fts, StoreSearch.Tgram),
        Some("tsv"),
        Seq("name"),
        "wal",
        sqlLit,
      ).get
      where should include("tsv @@ to_tsquery")
      where should include("LIKE '%wal%'")
      where should include(" OR ")
    }

    "match middle-of-token substring via tgram only (e.g. let in DetectorWallet)" in {
      val where = StoreFts.postgresSearchWhere(
        Set(StoreSearch.Tgram),
        None,
        Seq("name"),
        "let",
        sqlLit,
      ).get
      where shouldBe "(lower(coalesce(name, '')) LIKE '%let%')"
    }

    "not match middle-of-token via fts-only prefix query" in {
      StoreFts.postgresSearchWhere(
        Set(StoreSearch.Fts),
        Some("tsv"),
        Seq("name"),
        "let",
        sqlLit,
      ).get shouldBe "(tsv @@ to_tsquery('simple', 'let:*'))"
    }
  }
}
