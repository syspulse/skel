package io.syspulse.skel.wf.temporal.workflow.store

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import io.hacken.ext.wf.{WorkflowRun, WorkflowStep}

class WorkflowRunStoreSpec extends AnyWordSpec with Matchers {

  val timeout = 5.seconds

  // Helper to create test steps
  def testSteps(ids: Int*): Seq[WorkflowStep] = ids.map { id =>
    WorkflowStep(id, s"Step$id", "AUTO")
  }

  "WorkflowRunStoreMem" should {

    "store and retrieve workflow runs" in {
      val store = new WorkflowRunStoreMem()

      val run1 = WorkflowRun(
        wid = "workflow-1",
        rid = Some("run-1"),
        status = "NEW",
        cursor = -1,
        schema = 1,
        steps = testSteps(1, 2, 3)
      )

      // Add run
      Await.result(store.+(run1), timeout) shouldBe run1

      // Retrieve by rid
      store.??("run-1") shouldBe Some(run1)

      // Retrieve by wid (when rid is not provided)
      store.??("workflow-1") should not be None
    }

    "update existing workflow runs" in {
      val store = new WorkflowRunStoreMem()

      val run = WorkflowRun(
        wid = "workflow-1",
        rid = Some("run-1"),
        status = "NEW",
        cursor = -1,
        schema = 1,
        steps = testSteps(1, 2, 3)
      )

      Await.result(store.+(run), timeout)

      // Update status and cursor
      val updatedRun = run.copy(status = "RUNNING", cursor = 1)
      Await.result(store.+(updatedRun), timeout) shouldBe updatedRun

      // Verify update
      store.??("run-1") shouldBe Some(updatedRun)
      store.??("run-1").map(_.status) shouldBe Some("RUNNING")
      store.??("run-1").map(_.cursor) shouldBe Some(1)
    }

    "delete workflow runs" in {
      val store = new WorkflowRunStoreMem()

      val run = WorkflowRun(
        wid = "workflow-1",
        rid = Some("run-1"),
        status = "NEW",
        cursor = -1,
        schema = 1,
        steps = testSteps(1, 2, 3)
      )

      Await.result(store.+(run), timeout)
      store.??("run-1") should not be None

      // Delete
      Await.result(store.del("run-1"), timeout) shouldBe "run-1"
      store.??("run-1") shouldBe None
    }

    "return all workflow runs" in {
      val store = new WorkflowRunStoreMem()

      val run1 = WorkflowRun("workflow-1", Some("run-1"), "NEW", -1, 1, testSteps(1, 2, 3))
      val run2 = WorkflowRun("workflow-2", Some("run-2"), "RUNNING", 1, 2, testSteps(4, 5, 6))
      val run3 = WorkflowRun("workflow-3", Some("run-3"), "FINISHED", 3, 3, testSteps(7, 8, 9))

      Await.result(store.+(run1), timeout)
      Await.result(store.+(run2), timeout)
      Await.result(store.+(run3), timeout)

      val all = Await.result(store.all, timeout)
      all.size shouldBe 3
      all should contain allOf (run1, run2, run3)
    }

    "return correct size" in {
      val store = new WorkflowRunStoreMem()

      Await.result(store.size, timeout) shouldBe 0

      Await.result(store.+(WorkflowRun("w1", Some("r1"), "NEW", -1, 1, testSteps(1))), timeout)
      Await.result(store.size, timeout) shouldBe 1

      Await.result(store.+(WorkflowRun("w2", Some("r2"), "NEW", -1, 1, testSteps(1))), timeout)
      Await.result(store.size, timeout) shouldBe 2

      Await.result(store.del("r1"), timeout)
      Await.result(store.size, timeout) shouldBe 1
    }

    "handle workflow runs without rid" in {
      val store = new WorkflowRunStoreMem()

      val run = WorkflowRun(
        wid = "workflow-1",
        rid = None,
        status = "NEW",
        cursor = -1,
        schema = 1,
        steps = testSteps(1, 2, 3)
      )

      // Add run (will use wid as key)
      Await.result(store.+(run), timeout) shouldBe run

      // Retrieve by wid
      store.??("workflow-1") shouldBe Some(run)
    }

    "return failure when retrieving non-existent run" in {
      val store = new WorkflowRunStoreMem()

      store.??("non-existent") shouldBe None

      store.???("non-existent") match {
        case Failure(e) => e.getMessage should include("workflow run")
        case Success(_) => fail("Should return failure for non-existent run")
      }
    }
  }

  "WorkflowRunStoreDir" should {

    "persist workflow runs to disk" in {
      // Create temporary directory
      val tempDir = java.nio.file.Files.createTempDirectory("workflow-run-test").toString

      try {
        val store = new WorkflowRunStoreDir(tempDir)

        val run = WorkflowRun(
          wid = "workflow-1",
          rid = Some("run-1"),
          status = "NEW",
          cursor = -1,
          schema = 1,
          steps = testSteps(1, 2, 3)
        )

        // Add run
        Await.result(store.+(run), timeout) shouldBe run

        // Verify file exists
        val file = new java.io.File(s"${tempDir}/run-1.json")
        file.exists() shouldBe true

        // Create new store instance and verify data persisted
        val store2 = new WorkflowRunStoreDir(tempDir)
        store2.??("run-1") shouldBe Some(run)

      } finally {
        // Cleanup
        val dir = new java.io.File(tempDir)
        if (dir.exists()) {
          dir.listFiles().foreach(_.delete())
          dir.delete()
        }
      }
    }
  }
}
