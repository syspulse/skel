package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import io.hacken.ext.wf._
import io.syspulse.skel.wf.ext.store.WorkflowStoreDir

class WorkflowStoreDirSpec extends AnyWordSpec with Matchers {
  val timeout = Duration(10, "seconds")

  def graf(id: Int): WorkflowGraf =
    WorkflowGraf(id = id, sid = Some(id))
      .withNode(WorkflowNode(id = 0, title = "a", sid = 100))
      .withNode(WorkflowNode(id = 1, title = "b", sid = 101))
      .withLink(WorkflowLink(id = 0, from = 0, to = 1))

  def tmpDir(): String = java.nio.file.Files.createTempDirectory("wf-ext-test-").toString

  "WorkflowStoreDir" should {

    "persist schema/config/graf to files and reload them in a fresh store" in {
      val dir = tmpDir()

      val store1 = new WorkflowStoreDir(dir)
      val schema = WorkflowSchema.of(0, "W0", graf(0))
      Await.result(store1.addWSchema(schema), timeout)
      val config = WorkflowConfig.from(0, schema, oid = Some("owner-1"), xid = Some("run-1"))
      Await.result(store1.addWConf(config), timeout)
      Await.result(store1.addGraf(graf(3)), timeout)

      // fresh store pointed at the same dir reloads everything
      val store2 = new WorkflowStoreDir(dir)
      Await.result(store2.sizeWSchemas, timeout) shouldBe 1L
      Await.result(store2.sizeWConfs, timeout) shouldBe 1L
      Await.result(store2.sizeGrafs, timeout) shouldBe 1L

      Await.result(store2.getWSchema(0), timeout).name shouldBe "W0"
      Await.result(store2.getWConf(0), timeout).oid shouldBe Some("owner-1")
      Await.result(store2.findWConfByXid("run-1"), timeout).map(_.id) shouldBe Some(0)
      Await.result(store2.getGraf(3), timeout).nodes.keySet shouldBe Set(0, 1)
    }

    "persist DetectorSchema.schema/uiSchema and DetectorConfig nested schema on update+reload" in {
      import spray.json._
      import io.hacken.ext.detector._
      import io.hacken.ext.detector.DetectorSchemaJson._
      import io.hacken.ext.detector.DetectorConfigJson._

      val dir = tmpDir()
      val now = System.currentTimeMillis()
      val sch = JsObject("type" -> JsString("object"), "properties" -> JsObject(
        "severity" -> JsObject("type" -> JsString("number"), "default" -> JsNumber(0.5))))
      val ui = JsObject("ui:order" -> JsArray(JsString("severity")))

      val store1 = new WorkflowStoreDir(dir)
      val ds = DetectorSchema(0, now, now, "ACTIVE", "Scan", "1.0.0", "Scan", "", "", None, None, Seq(), Seq(), Some(sch), Some(ui))
      Await.result(store1.addDSchema(ds), timeout)
      val dc = DetectorConfig(0, now, now, "ACTIVE",
        DetectorConfigContract(0, now, now, 0, 0, None, None, None, None, "scan-1"),
        Some(DetectorConfigSchema(0, now, now, "ACTIVE", "Scan", "1.0.0", Some(sch), Some(ui))),
        "scan-1", "src", Seq(), Some(JsObject("severity" -> JsNumber(0.9))), Seq())
      Await.result(store1.addDConf(dc), timeout)

      // update DetectorSchema (name only) must keep schema/uiSchema
      Await.result(store1.addDSchema(ds.copy(name = "Scan2", updatedAt = now + 1)), timeout)
      // update DetectorConfig (status only) must keep nested schema/uiSchema + config
      Await.result(store1.addDConf(dc.copy(status = "DISABLED", updatedAt = now + 1)), timeout)

      val store2 = new WorkflowStoreDir(dir)
      val ds2 = Await.result(store2.getDSchema(0), timeout).get
      ds2.name shouldBe "Scan2"
      ds2.schema shouldBe Some(sch)
      ds2.uiSchema shouldBe Some(ui)

      val dc2 = Await.result(store2.getDConf(0), timeout).get
      dc2.status shouldBe "DISABLED"
      dc2.config.flatMap(_.fields.get("severity")) shouldBe Some(JsNumber(0.9))
      dc2.schema.flatMap(_.schema) shouldBe Some(sch)
      dc2.schema.flatMap(_.uiSchema) shouldBe Some(ui)
    }

    "remove files on delete" in {
      val dir = tmpDir()
      val store = new WorkflowStoreDir(dir)
      Await.result(store.addWSchema(WorkflowSchema.of(0, "W0", graf(0))), timeout)
      Await.result(store.delWSchema(0), timeout)

      val store2 = new WorkflowStoreDir(dir)
      Await.result(store2.sizeWSchemas, timeout) shouldBe 0L
    }
  }
}
