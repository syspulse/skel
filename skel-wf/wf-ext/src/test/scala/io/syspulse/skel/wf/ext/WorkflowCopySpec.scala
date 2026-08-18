package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import spray.json._
import io.hacken.ext.wf._
import io.hacken.ext.detector._
import io.syspulse.skel.wf.ext.store.{WorkflowCopy, WorkflowStoreMem}

class WorkflowCopySpec extends AnyWordSpec with Matchers {
  val timeout = Duration(5, "seconds")

  def graf(id: Int): WorkflowGraf =
    WorkflowGraf(id = id, sid = Some(id))
      .withNode(WorkflowNode(id = 0, title = "a", sid = 100))
      .withNode(WorkflowNode(id = 1, title = "b", sid = 101))
      .withLink(WorkflowLink(id = 0, from = 0, to = 1))

  def wschema(id: Int): WorkflowSchema = WorkflowSchema.of(id, s"W${id}", graf(id))

  def dschema(id: Int): DetectorSchema = {
    val now = 1000L
    DetectorSchema(id, now, now, "ACTIVE", s"Schema_${id}", "1.0.0", s"Title ${id}", "", "",
      None, None, Seq("t1"), Seq(), None, None)
  }

  def dconf(id: Int): DetectorConfig = {
    val now = 1000L
    DetectorConfig(id, now, now, "ACTIVE",
      DetectorConfigContract(0, now, now, 0, 0, None, None, None, None, s"cfg${id}"),
      None, s"cfg${id}", "SRC", Seq("s1"),
      config = Some(JsObject("k" -> JsString(s"v${id}"))), destinations = Seq())
  }

  def seed(src: WorkflowStoreMem): Unit = {
    Await.result(src.addWSchema(wschema(0)), timeout)
    Await.result(src.addWSchema(wschema(1)), timeout)
    Await.result(src.addDSchema(dschema(0)), timeout)
    Await.result(src.addDSchema(dschema(1)), timeout)
    Await.result(src.addDConf(dconf(0)), timeout)
    Await.result(src.addDConf(dconf(1)), timeout)
    Await.result(src.addWConf(WorkflowConfig.from(0, wschema(0), oid = Some("o0"))), timeout)
    Await.result(src.addWConf(WorkflowConfig.from(1, wschema(1), oid = Some("o1"))), timeout)
  }

  "WorkflowCopy" should {

    "copy all entity types in order WorkflowSchema, DetectorSchema, DetectorConfig, WorkflowConfig" in {
      val src = new WorkflowStoreMem
      val dst = new WorkflowStoreMem
      seed(src)

      val r = Await.result(WorkflowCopy(src, dst, "all", None), timeout)
      r.copied shouldBe Seq(
        "WorkflowSchema(0)", "WorkflowSchema(1)",
        "DetectorSchema(0)", "DetectorSchema(1)",
        "DetectorConfig(0)", "DetectorConfig(1)",
        "WorkflowConfig(0)", "WorkflowConfig(1)"
      )
      r.errors shouldBe empty

      Await.result(dst.sizeWSchemas, timeout) shouldBe 2L
      Await.result(dst.sizeDSchemas, timeout) shouldBe 2L
      Await.result(dst.sizeDConfs, timeout) shouldBe 2L
      Await.result(dst.sizeWConfs, timeout) shouldBe 2L
      Await.result(dst.getWSchema(1), timeout).name shouldBe "W1"
      Await.result(dst.getDSchema(1), timeout).get.name shouldBe "Schema_1"
      Await.result(dst.getDConf(1), timeout).get.name shouldBe "cfg1"
      Await.result(dst.getWConf(0), timeout).oid shouldBe Some("o0")
    }

    "copy a single WorkflowSchema by id and leave other types empty" in {
      val src = new WorkflowStoreMem
      val dst = new WorkflowStoreMem
      seed(src)

      val r = Await.result(WorkflowCopy(src, dst, "WorkflowSchema", Some(1)), timeout)
      r.copied shouldBe Seq("WorkflowSchema(1)")
      Await.result(dst.sizeWSchemas, timeout) shouldBe 1L
      Await.result(dst.getWSchema(1), timeout).name shouldBe "W1"
      Await.result(dst.sizeWConfs, timeout) shouldBe 0L
      Await.result(dst.sizeDSchemas, timeout) shouldBe 0L
    }

    "copy every DetectorConfig when id is omitted" in {
      val src = new WorkflowStoreMem
      val dst = new WorkflowStoreMem
      seed(src)

      val r = Await.result(WorkflowCopy(src, dst, "DetectorConfig", None), timeout)
      r.copied shouldBe Seq("DetectorConfig(0)", "DetectorConfig(1)")
      Await.result(dst.sizeDConfs, timeout) shouldBe 2L
      Await.result(dst.sizeWSchemas, timeout) shouldBe 0L
    }

    "record a missing id as an error and still succeed" in {
      val src = new WorkflowStoreMem
      val dst = new WorkflowStoreMem
      val r = Await.result(WorkflowCopy(src, dst, "DetectorSchema", Some(99)), timeout)
      r.copied shouldBe empty
      r.errors.map(e => (e.typ, e.id, e.cause)) shouldBe Seq(("DetectorSchema", 99, "not found"))
      r.summary should include ("errors (1): DetectorSchema(99): not found")
    }

    "skip a failed entity, continue, and report entity+id in the summary" in {
      val src = new WorkflowStoreMem
      val dst = new WorkflowStoreMem {
        override def addDConf(dconf: DetectorConfig) =
          if (dconf.id == 0) Future.failed(new RuntimeException("boom"))
          else super.addDConf(dconf)
      }
      seed(src)

      val r = Await.result(WorkflowCopy(src, dst, "DetectorConfig", None), timeout)
      r.copied shouldBe Seq("DetectorConfig(1)")
      r.errors.map(e => (e.typ, e.id, e.cause)) shouldBe Seq(("DetectorConfig", 0, "boom"))
      Await.result(dst.getDConf(1), timeout).get.name shouldBe "cfg1"
      Await.result(dst.getDConf(0), timeout) shouldBe None
      r.summary should include ("copied (1): DetectorConfig(1)")
      r.summary should include ("errors (1): DetectorConfig(0): boom")
    }

    "update an existing destination object instead of failing" in {
      val src = new WorkflowStoreMem
      val dst = new WorkflowStoreMem
      Await.result(dst.addWSchema(wschema(0).copy(name = "OLD")), timeout)
      Await.result(dst.addDSchema(dschema(0).copy(name = "OLD_DS")), timeout)
      Await.result(dst.addDConf(dconf(0).copy(name = "OLD_DC")), timeout)
      Await.result(dst.addWConf(WorkflowConfig.from(0, wschema(0), oid = Some("old"))), timeout)
      seed(src)

      val r = Await.result(WorkflowCopy(src, dst, "all", None), timeout)
      r.errors shouldBe empty
      Await.result(dst.getWSchema(0), timeout).name shouldBe "W0"
      Await.result(dst.getDSchema(0), timeout).get.name shouldBe "Schema_0"
      Await.result(dst.getDConf(0), timeout).get.name shouldBe "cfg0"
      Await.result(dst.getWConf(0), timeout).oid shouldBe Some("o0")
    }

    "reject an id with type all" in {
      val src = new WorkflowStoreMem
      val dst = new WorkflowStoreMem
      intercept[IllegalArgumentException] {
        Await.result(WorkflowCopy(src, dst, "all", Some(0)), timeout)
      }
    }
  }
}
