package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import spray.json._
import io.hacken.ext.wf._
import io.hacken.ext.wf.WorkflowConfigJson._
import io.syspulse.skel.wf.ext.store.WorkflowStoreMem

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
  }
}
