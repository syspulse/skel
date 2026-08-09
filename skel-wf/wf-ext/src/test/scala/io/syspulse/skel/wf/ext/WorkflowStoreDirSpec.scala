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
