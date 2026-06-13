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
      Await.result(store.addConfig(c), timeout)
      Await.result(store.getConfig(0), timeout).id shouldBe 0
      Await.result(store.sizeConfigs, timeout) shouldBe 1L

      Await.result(store.addConfig(c.copy(title = "t2")), timeout)
      Await.result(store.getConfig(0), timeout).title shouldBe "t2"

      Await.result(store.delConfig(0), timeout) shouldBe 0
      Await.result(store.getConfigOpt(0), timeout) shouldBe None
    }

    "findConfigByOid returns all configs of an owner" in {
      val store = new WorkflowStoreMem()
      Await.result(store.addConfig(WorkflowConfig.from(0, schema(0), oid = Some("owner-A"))), timeout)
      Await.result(store.addConfig(WorkflowConfig.from(1, schema(0), oid = Some("owner-A"))), timeout)
      Await.result(store.addConfig(WorkflowConfig.from(2, schema(0), oid = Some("owner-B"))), timeout)

      Await.result(store.findConfigByOid("owner-A"), timeout).map(_.id).toSet shouldBe Set(0, 1)
      Await.result(store.findConfigByOid("owner-B"), timeout).map(_.id) shouldBe Seq(2)
      Await.result(store.findConfigByOid("missing"), timeout) shouldBe empty
    }

    "findConfigByXid returns a single config (case-insensitive)" in {
      val store = new WorkflowStoreMem()
      Await.result(store.addConfig(WorkflowConfig.from(0, schema(0), xid = Some("RUN-1"))), timeout)
      Await.result(store.findConfigByXid("run-1"), timeout).map(_.id) shouldBe Some(0)
      Await.result(store.findConfigByXid("nope"), timeout) shouldBe None
    }

    "page configs (from/size) and report total" in {
      val store = new WorkflowStoreMem()
      (0 until 7).foreach(i => Await.result(store.addConfig(WorkflowConfig.from(i, schema(0))), timeout))
      val p = Await.result(store.listConfigs(Some(0), Some(5)), timeout)
      p.total shouldBe 7L
      p.configs should have size 5
    }
  }
}
