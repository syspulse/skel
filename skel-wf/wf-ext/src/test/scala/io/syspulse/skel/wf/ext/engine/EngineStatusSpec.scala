package io.syspulse.skel.wf.ext.engine

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EngineStatusSpec extends AnyWordSpec with Matchers {

  "EngineStatus.fromTemporalWorkflow" should {

    "map proto enum names to normalized statuses" in {
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_RUNNING")    shouldBe EngineStatus.RUNNING
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_COMPLETED")  shouldBe EngineStatus.COMPLETED
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_FAILED")     shouldBe EngineStatus.FAILED
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_TERMINATED") shouldBe EngineStatus.TERMINATED
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_CANCELED")   shouldBe EngineStatus.CANCELED
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_TIMED_OUT")  shouldBe EngineStatus.TIMED_OUT
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW") shouldBe EngineStatus.CONTINUED_AS_NEW
    }

    "map short/CLI names as well" in {
      EngineStatus.fromTemporalWorkflow("Running")    shouldBe EngineStatus.RUNNING
      EngineStatus.fromTemporalWorkflow("Completed")  shouldBe EngineStatus.COMPLETED
      EngineStatus.fromTemporalWorkflow("Terminated") shouldBe EngineStatus.TERMINATED
      EngineStatus.fromTemporalWorkflow("Canceled")   shouldBe EngineStatus.CANCELED
    }

    "map unknown/unspecified to UNKNOWN" in {
      EngineStatus.fromTemporalWorkflow("WORKFLOW_EXECUTION_STATUS_UNSPECIFIED") shouldBe EngineStatus.UNKNOWN
      EngineStatus.fromTemporalWorkflow("")      shouldBe EngineStatus.UNKNOWN
      EngineStatus.fromTemporalWorkflow("gibber") shouldBe EngineStatus.UNKNOWN
    }
  }

  "EngineStatus.fromTemporalActivity" should {
    "map activity lifecycle to normalized statuses" in {
      EngineStatus.fromTemporalActivity("SCHEDULED") shouldBe EngineStatus.SCHEDULED
      EngineStatus.fromTemporalActivity("STARTED")   shouldBe EngineStatus.RUNNING
      EngineStatus.fromTemporalActivity("COMPLETED") shouldBe EngineStatus.COMPLETED
      EngineStatus.fromTemporalActivity("FAILED")    shouldBe EngineStatus.FAILED
      EngineStatus.fromTemporalActivity("TIMED_OUT") shouldBe EngineStatus.TIMED_OUT
    }
  }

  "EngineStatus.isTerminal" should {
    "classify closed statuses" in {
      EngineStatus.isTerminal(EngineStatus.COMPLETED)  shouldBe true
      EngineStatus.isTerminal(EngineStatus.FAILED)     shouldBe true
      EngineStatus.isTerminal(EngineStatus.TERMINATED) shouldBe true
      EngineStatus.isTerminal(EngineStatus.CANCELED)   shouldBe true
      EngineStatus.isTerminal(EngineStatus.RUNNING)       shouldBe false
      EngineStatus.isTerminal(EngineStatus.RUNNING_RETRY) shouldBe false
      EngineStatus.isTerminal(EngineStatus.WAITING)       shouldBe false
    }
  }
}
