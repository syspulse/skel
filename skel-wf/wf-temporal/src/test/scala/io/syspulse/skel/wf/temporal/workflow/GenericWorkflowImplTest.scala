package io.syspulse.skel.wf.temporal.workflow

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import io.hacken.ext.wf.WorkflowRun

class GenericWorkflowImplTest extends AnyWordSpec with Matchers {

  "GenericWorkflowImpl" should {
    "have correct workflow interface" in {
      // Verify the class implements GenericWorkflow interface
      classOf[GenericWorkflow].isAssignableFrom(classOf[GenericWorkflowImpl]) shouldBe true
    }

    "have correct execute method signature" in {
      // Verify the execute method exists with correct signature
      // Note: Cannot instantiate GenericWorkflowImpl outside of Temporal runtime
      val executeMethod = classOf[GenericWorkflowImpl].getMethod("execute",
        classOf[WorkflowRun],
        classOf[java.util.Map[_, _]],
        classOf[java.util.Map[_, _]]
      )
      executeMethod should not be null
      executeMethod.getReturnType shouldBe classOf[WorkflowRun]
    }

    "have correct signal method signature" in {
      val continueMethod = classOf[GenericWorkflowImpl].getMethod("continueWorkflow", classOf[Int])
      continueMethod should not be null
    }

    "have correct query method signatures" in {
      val getRunMethod = classOf[GenericWorkflowImpl].getMethod("getWorkflowRun")
      getRunMethod should not be null
      getRunMethod.getReturnType shouldBe classOf[WorkflowRun]

      val getStatusMethod = classOf[GenericWorkflowImpl].getMethod("getStatus")
      getStatusMethod should not be null
      getStatusMethod.getReturnType shouldBe classOf[String]

      val getCursorMethod = classOf[GenericWorkflowImpl].getMethod("getCursor")
      getCursorMethod should not be null
      getCursorMethod.getReturnType shouldBe classOf[Int]
    }
  }
}
