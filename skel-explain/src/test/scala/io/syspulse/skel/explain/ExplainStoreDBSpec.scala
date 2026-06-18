package io.syspulse.skel.explain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import java.sql.DriverManager

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

import io.syspulse.skel.config.{Configuration, ConfigurationMap}
import io.syspulse.skel.explain.store.ExplainStoreDB

class ExplainStoreDBSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val timeout = Duration(30, "seconds")

  var embeddedPg: EmbeddedPostgres = _
  var store: ExplainStoreDB = _
  var jdbcUrl: String = _

  private def newStore(): ExplainStoreDB = {
    val cfgMap = new ConfigurationMap()
    cfgMap + ("postgres.url", jdbcUrl)
    cfgMap + ("postgres.database", "postgres")
    cfgMap + ("postgres.username", "postgres")
    cfgMap + ("postgres.password", "postgres")
    cfgMap + ("postgres.numThreads", "2")
    new ExplainStoreDB(new Configuration(Seq(cfgMap)), "postgres://postgres", None)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    embeddedPg = EmbeddedPostgres.builder().start()
    val port = embeddedPg.getPort()
    jdbcUrl = s"jdbc:postgresql://localhost:$port/postgres"
    store = newStore()
  }

  override def afterAll(): Unit = {
    if (embeddedPg != null) embeddedPg.close()
    super.afterAll()
  }

  "ExplainStoreDB" should {
    
    "update meta on upsert with same oid/rid" in {
      val rid = s"MetaTest_${System.nanoTime()}"
      val rule1 = Explain(
        oid = Some("490"),
        rid = rid,
        scripts = Seq(ExplainScript("str", "")),
        name = Some("Meta rule"),
        meta = Some(Map("icon" -> "old-icon.png")),
      )
      val rule2 = rule1.copy(
        meta = Some(Map(
          "icon" -> "https://example.com/icon.png",
          "width" -> 500,
          "color" -> "#ff0",
        )),
        ts = rule1.ts + 1,
      )

      Await.result(store.+(rule1), timeout)
      Await.result(store.+(rule2), timeout)

      val loaded = Await.result(store.get(Some("490"), rid), timeout)
      loaded.meta shouldBe defined
      loaded.meta.get.get("icon") shouldBe Some("https://example.com/icon.png")
      loaded.meta.get.get("width") shouldBe Some(500)
      loaded.meta.get.get("color") shouldBe Some("#ff0")
    }
  }
}
