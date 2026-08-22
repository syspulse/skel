package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

import io.hacken.ext.wf.{WorkflowGraf, WorkflowNode}
import io.syspulse.skel.wf.ext.store.WorkflowStore

class WorkflowStoreUriSanitizeSpec extends AnyWordSpec with Matchers {

  val safeSvg = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="8"/></svg>"""

  "WorkflowStore.uriSanitize(graf)" should {

    "reject a graph node icon that injects SVG foreignObject" in {
      val g = WorkflowGraf(id = 0).withNode(WorkflowNode(id = 0, title = "n", sid = 1,
        icon = Some("""<svg><foreignObject><script>alert(1)</script></foreignObject></svg>""")))
      WorkflowStore.uriSanitize(g) shouldBe a [Failure[_]]
    }

    "accept a graph with a safe node icon" in {
      val g = WorkflowGraf(id = 0).withNode(WorkflowNode(id = 0, title = "n", sid = 1, icon = Some(safeSvg)))
      WorkflowStore.uriSanitize(g).map(_.nodes(0).icon) shouldBe Success(Some(safeSvg))
    }
  }
}
