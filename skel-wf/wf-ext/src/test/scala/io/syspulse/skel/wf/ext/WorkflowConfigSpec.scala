package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import spray.json._
import io.hacken.ext.wf._
import io.hacken.ext.wf.WorkflowConfigJson._
import io.syspulse.skel.wf.ext.store.{WorkflowStore, WorkflowStoreMem}

class WorkflowConfigSpec extends AnyWordSpec with Matchers {
  val timeout = Duration(5, "seconds")

  def graf(id: Int): WorkflowGraf =
    WorkflowGraf(id = id, sid = Some(id))
      .withNode(WorkflowNode(id = 0, title = "a", sid = 100))
      .withNode(WorkflowNode(id = 1, title = "b", sid = 101))
      .withLink(WorkflowLink(id = 0, from = 0, to = 1))

  def schema(id: Int): WorkflowSchema = WorkflowSchema.of(id, s"W${id}", graf(id))

  "WorkflowConfig entity" should {

    "be created from a WorkflowSchema, copying defaults and marking the graph as an instance" in {
      val s = schema(2)
      val c = WorkflowConfig.from(0, s)
      c.sid shouldBe 2
      c.name shouldBe s.name
      c.version shouldBe s.version
      c.graph.isInstance shouldBe true
      c.graph.cid shouldBe Some(0)
      // the schema's graph remains a template
      s.graph.isTemplate shouldBe true
    }

    "carry oid/pid/xid" in {
      val c = WorkflowConfig.from(1, schema(2), name = Some("custom"), oid = Some("owner-1"), pid = Some("proj-1"), xid = Some("run-xyz"))
      c.name shouldBe "custom"
      c.oid shouldBe Some("owner-1")
      c.pid shouldBe Some("proj-1")
      c.xid shouldBe Some("run-xyz")
    }

    "override title when provided (placeholders still substituted); name stays the schema type" in {
      val s = schema(2).copy(title = "schema-title")
      val c = WorkflowConfig.from(5, s, title = Some("custom-{id}"))
      c.title shouldBe "custom-5"
      c.name shouldBe s.name
      WorkflowConfig.from(5, s).title shouldBe "schema-title"
    }

    "override author when provided, else copy WorkflowSchema.author" in {
      val s = schema(2).copy(author = "schema-author")
      WorkflowConfig.from(1, s).author shouldBe "schema-author"
      WorkflowConfig.from(1, s, author = None).author shouldBe "schema-author"
      WorkflowConfig.from(1, s, author = Some("")).author shouldBe "schema-author"
      WorkflowConfig.from(1, s, author = Some("alice")).author shouldBe "alice"
    }

    "round-trip via JSON" in {
      val c = WorkflowConfig.from(1, schema(2), oid = Some("o1"), xid = Some("x1"))
      val c2 = c.toJson.convertTo[WorkflowConfig]
      c2 shouldBe c
    }
  }

  "WorkflowStore (config CRUD + lookups)" should {

    "create, read, update, delete a config" in {
      val store = new WorkflowStoreMem()
      val c = WorkflowConfig.from(0, schema(0))
      Await.result(store.addWConf(c), timeout)
      Await.result(store.getWConf(0), timeout).id shouldBe 0
      Await.result(store.sizeWConfs, timeout) shouldBe 1L

      Await.result(store.addWConf(c.copy(title = "t2")), timeout)
      Await.result(store.getWConf(0), timeout).title shouldBe "t2"

      Await.result(store.delWConf(0), timeout) shouldBe 0
      Await.result(store.getWConfOpt(0), timeout) shouldBe None
    }

    "findWConfByOid returns all configs of an owner" in {
      val store = new WorkflowStoreMem()
      Await.result(store.addWConf(WorkflowConfig.from(0, schema(0), oid = Some("owner-A"))), timeout)
      Await.result(store.addWConf(WorkflowConfig.from(1, schema(0), oid = Some("owner-A"))), timeout)
      Await.result(store.addWConf(WorkflowConfig.from(2, schema(0), oid = Some("owner-B"))), timeout)

      Await.result(store.findWConfByOid("owner-A"), timeout).map(_.id).toSet shouldBe Set(0, 1)
      Await.result(store.findWConfByOid("owner-B"), timeout).map(_.id) shouldBe Seq(2)
      Await.result(store.findWConfByOid("missing"), timeout) shouldBe empty
    }

    "findWConfByXid returns a single config (case-insensitive)" in {
      val store = new WorkflowStoreMem()
      Await.result(store.addWConf(WorkflowConfig.from(0, schema(0), xid = Some("RUN-1"))), timeout)
      Await.result(store.findWConfByXid("run-1"), timeout).map(_.id) shouldBe Some(0)
      Await.result(store.findWConfByXid("nope"), timeout) shouldBe None
    }

    "page configs (from/size) and report total" in {
      val store = new WorkflowStoreMem()
      (0 until 7).foreach(i => Await.result(store.addWConf(WorkflowConfig.from(i, schema(0))), timeout))
      val p = Await.result(store.listWConfs(Some(0), Some(5)), timeout)
      p.total shouldBe 7L
      p.wconfs should have size 5
    }

    "filter configs by updatedAt time range (ts0..ts1), inclusive on both ends" in {
      val store = new WorkflowStoreMem()
      Seq(1000L, 2000L, 3000L).zipWithIndex.foreach { case (ts, i) =>
        Await.result(store.addWConf(WorkflowConfig.from(i, schema(0)).copy(updatedAt = ts)), timeout)
      }
      // ts0=1500, ts1=2500 -> only the 2000 one
      val mid = Await.result(store.listWConfs(None, None, None, None,
        WorkflowStore.WConfFilter(tsStart = Some(1500L), tsEnd = Some(2500L))), timeout)
      mid.total shouldBe 1L
      mid.wconfs.map(_.updatedAt) shouldBe Seq(2000L)
      // ts0=2000 (inclusive lower bound) -> 2000 + 3000
      Await.result(store.listWConfs(None, None, None, None,
        WorkflowStore.WConfFilter(tsStart = Some(2000L))), timeout).wconfs.map(_.updatedAt).toSet shouldBe Set(2000L, 3000L)
      // ts1=2000 (inclusive upper bound) -> 1000 + 2000
      Await.result(store.listWConfs(None, None, None, None,
        WorkflowStore.WConfFilter(tsEnd = Some(2000L))), timeout).wconfs.map(_.updatedAt).toSet shouldBe Set(1000L, 2000L)
      // no range -> all
      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter()), timeout).total shouldBe 3L
    }

    "filterSortWConfs applies the updatedAt range and default updatedAt-desc sort" in {
      val xs = Seq(1000L, 2000L, 3000L).zipWithIndex.map { case (ts, i) =>
        WorkflowConfig.from(i, schema(0)).copy(updatedAt = ts)
      }
      WorkflowStore
        .filterSortWConfs(xs, WorkflowStore.WConfFilter(tsStart = Some(2000L), tsEnd = Some(3000L)))
        .map(_.updatedAt) shouldBe Seq(3000L, 2000L)
    }

    "search configs by name/title (not xid/description), page, and reject short queries" in {
      val store = new WorkflowStoreMem()
      Await.result(store.addWConf(WorkflowConfig.from(0, schema(0)).copy(
        name = "PoR-Flow", title = "Proof of Reserve", xid = Some("flow-runtime"))), timeout)
      Await.result(store.addWConf(WorkflowConfig.from(1, schema(0)).copy(
        name = "Audit", title = "Workflow Audit", description = "PoR hidden")), timeout)
      Await.result(store.addWConf(WorkflowConfig.from(2, schema(0)).copy(
        name = "zzz", title = "zzz", xid = Some("por-xid"))), timeout)

      val por = Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("por"))), timeout)
      por.total shouldBe 1L
      por.wconfs.map(_.id) shouldBe Seq(0)

      val page = Await.result(store.listWConfs(Some(0), Some(1), None, None, WorkflowStore.WConfFilter(search = Some("audit"))), timeout)
      page.total shouldBe 1L
      page.wconfs should have size 1

      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("por-xid"))), timeout).total shouldBe 0L
      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("ab"))), timeout).total shouldBe 0L
    }

    "name/title search applies oid and pid filters" in {
      val store = new WorkflowStoreMem()
      Await.result(store.addWConf(WorkflowConfig.from(0, schema(0)).copy(
        name = "PoR-Flow", title = "Proof of Reserve", oid = Some("490"), pid = Some("474"))), timeout)
      Await.result(store.addWConf(WorkflowConfig.from(1, schema(0)).copy(
        name = "PoR-OtherProj", title = "Proof other project", oid = Some("490"), pid = Some("999"))), timeout)
      Await.result(store.addWConf(WorkflowConfig.from(2, schema(0)).copy(
        name = "PoR-OtherOid", title = "Proof other owner", oid = Some("530"), pid = Some("474"))), timeout)

      val q = WorkflowStore.WConfFilter(search = Some("proof"))
      val scoped = Await.result(store.listWConfs(None, None, Some("490"), Some("474"), q), timeout)
      scoped.total shouldBe 1L
      scoped.wconfs.map(_.id) shouldBe Seq(0)

      Await.result(store.listWConfs(None, None, Some("490"), None, q), timeout)
        .wconfs.map(_.id).toSet shouldBe Set(0, 1)
    }
  }
}
