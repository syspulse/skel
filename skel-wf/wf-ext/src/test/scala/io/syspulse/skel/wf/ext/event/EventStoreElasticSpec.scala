package io.syspulse.skel.wf.ext.event

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.Try

import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import spray.json._

/**
 * Local OpenSearch CRUD. Uses a dedicated test index and always deletes it in afterAll.
 * Skips when local OpenSearch is not reachable.
 *
 * Local docker (OpenSearch 2.13) in this environment is HTTPS on :9200 with a self-signed cert
 * (`?tls=ignore`). Credentials come from env.local / ES_USER+ES_PASSWORD when present.
 */
class EventStoreElasticSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.global
  val timeout = 20.seconds
  val index = s"detector-alert-wfext-test"

  lazy val uriStr: String = ElasticEnv.localUri(index)
  lazy val store: EventStoreElastic = EventStoreElastic(uriStr)

  def available: Boolean = Try {
    Await.result(store.ensureIndex(), timeout)
    true
  }.getOrElse(false)

  override def afterAll(): Unit = {
    Try(Await.result(store.dropIndex(), timeout))
    Try(store.close())
    super.afterAll()
  }

  def ev(eid: String, oid: Long = 560L, cid: Long = 5818L, did: Long = 12913L, ts: Long = 1606311430006L, desc: String = "d"): Alert =
    Alert.fromCreate(EventCreateReq(
      ts = ts, eid = eid, rid = Some("tx-1"), oid = oid, pid = 1789L, cid = cid, did = did,
      nid = "SafeMultisigMonitor", name = Some("Safe Multisig Monitor"),
      wid = 7L, wna = Some("cfg-name"), wti = Some("cfg-title"),
      sid = Some("WORKFLOW"), sev = 0.25, desc = Some(desc),
      meta = Some(JsObject("k" -> JsString("v")))
    ))

  "yearlyIndex" should {
    "map a *-search alias and event ts to detector-alert-YYYY (UTC)" in {
      EventStoreElastic.yearlyIndex("detector-alert-search", 1606311430006L) shouldBe "detector-alert-2020"
      EventStoreElastic.yearlyIndex("detector-alert-search", 1789120771484L) shouldBe "detector-alert-2026"
    }
  }

  "EventStoreElastic (local)" should {

    "create index, upsert, get by Elastic key and eid" in {
      assume(available)
      val a = ev("loc-1")
      Await.result(store.upsert(Seq(a)), timeout).head.id shouldBe a.id

      val got = Await.result(store.getById(a.id), timeout).get
      got.eid shouldBe "loc-1"
      got.teid shouldBe 560L
      got.coid shouldBe 5818L
      got.deid shouldBe 12913L
      got.nse shouldBe 0.25
      got.se shouldBe "MEDIUM"
      got.wid shouldBe 7L
      got.wna shouldBe Some("cfg-name")
      got.wti shouldBe Some("cfg-title")
      got.meta.get.fields("k") shouldBe JsString("v")
      got.meta.get.fields("wid") shouldBe JsNumber(7)
      got.meta.get.fields("wna") shouldBe JsString("cfg-name")
      got.meta.get.fields("wti") shouldBe JsString("cfg-title")

      Await.result(store.getByEid("loc-1"), timeout).map(_.id) shouldBe Seq(a.id)
    }

    "overwrite the same eid (same _id) including a changed timestamp" in {
      assume(available)
      Await.result(store.upsert(Seq(ev("loc-ow", ts = 1L, desc = "old"))), timeout)
      Await.result(store.upsert(Seq(ev("loc-ow", ts = 99L, desc = "new"))), timeout)
      val got = Await.result(store.getById(Alert.elasticId(12913L, "loc-ow")), timeout).get
      got.ts shouldBe 99L
      got.ame shouldBe "new"
    }

    "query by time range, oid, pid, did, sid and paginate" in {
      assume(available)
      Await.result(store.upsert(Seq(
        ev("lq1", oid = 1, ts = 10),
        ev("lq2", oid = 1, ts = 20),
        ev("lq3", oid = 2, ts = 30),
      )), timeout)

      val page = Await.result(store.query(EventQuery(oid = Some(1), ts0 = Some(10), ts1 = Some(20), from = Some(0), size = Some(10))), timeout)
      page.alerts.map(_.eid).toSet shouldBe Set("lq1", "lq2")
      page.total shouldBe 2L

      val paged = Await.result(store.query(EventQuery(oid = Some(1), from = Some(0), size = Some(1))), timeout)
      paged.alerts should have size 1
      paged.total shouldBe 2L
    }

    "query by cid (coid)" in {
      assume(available)
      Await.result(store.upsert(Seq(
        ev("cq1", oid = 3, cid = 5818L),
        ev("cq2", oid = 3, cid = 99L),
      )), timeout)

      val page = Await.result(store.query(EventQuery(oid = Some(3), cid = Some(5818L), from = Some(0), size = Some(10))), timeout)
      page.alerts.map(_.eid).toSet shouldBe Set("cq1")
      all(page.alerts.map(_.coid)) shouldBe 5818L
    }

    "delete by Elastic key and by eid" in {
      assume(available)
      val a = ev("loc-del")
      Await.result(store.upsert(Seq(a)), timeout)
      Await.result(store.delById(a.id), timeout) shouldBe true
      Await.result(store.getById(a.id), timeout) shouldBe None

      Await.result(store.upsert(Seq(ev("loc-del-eid"))), timeout)
      Await.result(store.delByEid("loc-del-eid"), timeout) should be >= 1
      Await.result(store.getByEid("loc-del-eid"), timeout) shouldBe empty
    }
  }
}

/**
 * Unreachable cluster: must fail immediately with the elastic4s exception (no 30s retry hang).
 */
class EventStoreElasticFailSpec extends AnyWordSpec with Matchers {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "EventStoreElastic" should {
    "fail fast with the elastic4s exception when OpenSearch is down" in {
      val store = EventStoreElastic("http://127.0.0.1:1/detector-alert-search")
      try {
        val err = intercept[Exception] {
          Await.result(store.query(EventQuery(from = Some(0), size = Some(1))), 5.seconds)
        }
        err should not be a [java.util.concurrent.TimeoutException]
        val chain = Iterator.iterate(err: Throwable)(_.getCause).takeWhile(_ != null).toSeq
        val fromElastic4s = chain.exists { e =>
          val n = e.getClass.getName
          val m = Option(e.getMessage).getOrElse("")
          n.contains("elastic4s") || m.contains("max retry timeout") || m.contains("blacklisted")
        }
        fromElastic4s shouldBe true
      } finally store.close()
    }

    "fail upsert the same way (not hang)" in {
      val store = EventStoreElastic("http://127.0.0.1:1/detector-alert-search")
      try {
        val alert = Alert.fromCreate(EventCreateReq(
          ts = 1L, eid = "e", oid = 1L, pid = 1L, cid = 1L, did = 1L, nid = "N", wid = 1L, sev = 0.1
        ))
        intercept[Exception] {
          Await.result(store.upsert(Seq(alert)), 5.seconds)
        } should not be a [java.util.concurrent.TimeoutException]
      } finally store.close()
    }
  }
}

object ElasticEnv {
  def loadKv(filename: String): Map[String, String] = {
    val name = Paths.get(filename).getFileName.toString
    val candidates = Seq(
      Paths.get(filename),
      Paths.get(name),
      Paths.get("skel-wf/wf-ext", name),
      Paths.get("wf-ext", name),
    ).distinct
    val p = candidates.find(Files.exists(_)).getOrElse(Paths.get(filename))
    if (!Files.exists(p)) return Map.empty
    val raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
    raw.split("\n").iterator
      .map(_.trim)
      .filter(l => l.nonEmpty && !l.startsWith("#") && l.contains("="))
      .map { l0 =>
        val l = if (l0.startsWith("export ")) l0.stripPrefix("export ").trim else l0
        val i = l.indexOf('=')
        val k = l.substring(0, i).trim
        var v = l.substring(i + 1).trim
        if ((v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) ||
            (v.startsWith("'") && v.endsWith("'") && v.length >= 2))
          v = v.substring(1, v.length - 1)
        else if (v.startsWith("\"") || v.startsWith("'"))
          v = v.substring(1)
        k -> v
      }
      .toMap
  }

  def localUri(index: String): String = {
    val file = loadKv("env.local")
    val user = sys.env.getOrElse("ES_USER", file.getOrElse("ES_USER", "admin"))
    val pass = sys.env.getOrElse("ES_PASSWORD", sys.env.getOrElse("ES_PASS", file.getOrElse("ES_PASSWORD", "Abcd_1234#")))
    val enc = URLEncoder.encode(pass, "UTF-8")
    s"https://${user}:${enc}@localhost:9200/${index}?tls=ignore"
  }

  def devSearchUri(): Option[String] = {
    val file = loadKv("env.dev")
    val host = sys.env.get("ELASTIC_URI").orElse(file.get("ELASTIC_URI")).map(_.stripSuffix("/"))
    val user = sys.env.get("ELASTIC_USER").orElse(file.get("ELASTIC_USER"))
    val pass = sys.env.get("ELASTIC_PASS").orElse(file.get("ELASTIC_PASS"))
    (host, user, pass) match {
      case (Some(h), Some(u), Some(p)) =>
        val hostOnly = h.replaceFirst("^https://", "").replaceFirst("^http://", "")
        val enc = URLEncoder.encode(p, "UTF-8")
        Some(s"https://${u}:${enc}@${hostOnly}/detector-alert-search")
      case _ => None
    }
  }
}
