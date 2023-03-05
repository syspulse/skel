package io.syspulse.skel.wf

import scala.util.{Try,Success,Failure}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.wf.runtime._

class WorkflowSpec extends AnyWordSpec with Matchers with WorkflowTestable {
  
  "WorkflowSpec" should {

    "create Workflow with 2 Logs" in {
      implicit val we = new WorkflowEngine

      val w1 = Workflow("wf-1",ExecData.empty,
        store = wfDir,
        flow = Seq(
          Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))),
          Exec("F-2","io.syspulse.skel.wf.exec.ProcessExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("err-0"))),
          Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
        ),
        links = Seq(
          Link("link-1","F-1","out-0","F-2","in-0"),
          Link("link-2","F-2","out-0","F-3","in-0")
        )
      )
      
      info(s"w1 = ${w1}")      
      
      val s2 = we.spawn(w1)
      info(s"s2 = ${s2}")

      // s1 should !== (s2)
    }

    // "run Workflowing with 5 errors and 3 retries as Failure" in {
    //   val p = new Workflowing[String]("Workflowing-1",
    //     stages = List(
    //       new StageError(5)
    //     )
    //   )
    //   val f1 = p.run("started")
    //   f1.data should === ("error: 4")
    // }
    
  }
}
