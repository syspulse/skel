package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import spray.json._
import io.hacken.ext.wf._
import io.hacken.ext.wf.WorkflowSchemaJson._
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowStore}

class WorkflowSchemaSpec extends AnyWordSpec with Matchers {
  val timeout = Duration(5, "seconds")

  def graf(id: Int): WorkflowGraf =
    WorkflowGraf(id = id, sid = Some(id))
      .withNode(WorkflowNode(id = 0, name = "a", sid = 100))
      .withNode(WorkflowNode(id = 1, name = "b", sid = 101))
      .withLink(WorkflowLink(id = 0, from = 0, to = 1))

  "WorkflowSchema entity" should {
    "build with `of` defaults" in {
      val s = WorkflowSchema.of(0, "WorkflowDemo", graf(0))
      s.id shouldBe 0
      s.name shouldBe "WorkflowDemo"
      s.status shouldBe WorkflowSchema.Status.ACTIVE
      s.version shouldBe WorkflowSchema.Version.DEF_VERSION
      s.graph.isTemplate shouldBe true
    }

    "round-trip via JSON preserving the graph" in {
      val s = WorkflowSchema.of(3, "WorkflowAudit", graf(3))
      val s2 = s.toJson.convertTo[WorkflowSchema]
      s2 shouldBe s
      s2.graph.nodes.keySet shouldBe Set(0, 1)
      s2.graph.links.keySet shouldBe Set(0)
    }
  }

  "WorkflowStore (schema CRUD)" should {

    "create, read, update, delete" in {
      val store = new WorkflowStoreMem()
      val s = WorkflowSchema.of(0, "W0", graf(0))

      Await.result(store.addSchema(s), timeout)
      Await.result(store.getSchema(0), timeout).name shouldBe "W0"
      Await.result(store.getSchemaOpt(0), timeout) shouldBe Some(s)
      Await.result(store.sizeSchemas, timeout) shouldBe 1L

      val updated = s.copy(name = "W0-renamed")
      Await.result(store.addSchema(updated), timeout)
      Await.result(store.getSchema(0), timeout).name shouldBe "W0-renamed"

      Await.result(store.delSchema(0), timeout) shouldBe 0
      Await.result(store.sizeSchemas, timeout) shouldBe 0L
    }

    "fail get/delete of a missing schema" in {
      val store = new WorkflowStoreMem()
      Await.result(store.getSchemaOpt(99), timeout) shouldBe None
      intercept[Exception] { Await.result(store.getSchema(99), timeout) }
      intercept[Exception] { Await.result(store.delSchema(99), timeout) }
    }

    "assign next ids starting at 0, never negative" in {
      val store = new WorkflowStoreMem()
      Await.result(store.nextSchemaId, timeout) shouldBe 0
      Await.result(store.addSchema(WorkflowSchema.of(0, "a", graf(0))), timeout)
      Await.result(store.addSchema(WorkflowSchema.of(5, "b", graf(5))), timeout)
      Await.result(store.nextSchemaId, timeout) shouldBe 6
    }

    "page schemas (from/size) and report total" in {
      val store = new WorkflowStoreMem()
      (0 until 10).foreach(i => Await.result(store.addSchema(WorkflowSchema.of(i, s"w${i}", graf(i))), timeout))

      val p = Await.result(store.listSchemas(Some(2), Some(3)), timeout)
      p.total shouldBe 10L
      p.schemas should have size 3

      val all = Await.result(store.listSchemas(None, None), timeout)
      all.schemas should have size 10
      all.total shouldBe 10L
    }
  }
}
