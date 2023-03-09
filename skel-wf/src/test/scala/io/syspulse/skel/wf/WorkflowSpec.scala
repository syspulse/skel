package io.syspulse.skel.wf

import scala.util.{Try,Success,Failure}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.runtime.thread._
import io.syspulse.skel.wf.runtime.actor.RuntimeActors

class WorkflowSpec extends AnyWordSpec with Matchers with WorkflowTestable {
  
  "WorkflowSpec" should {
    
    "create RuntimeThreads Workflow with 2 Logs" in {
      implicit val we = new WorkflowEngine(runtime = new RuntimeThreads())

      val w1 = Workflow("wf-1",ExecData.empty,
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
      
      val wf1 = we.spawn(w1)
      info(s"wf = ${wf1}")

      we.start(wf1.get)

      Thread.sleep(100L)

      // val r1 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map("k1"->"v1"))))
      // info(s"r1 = ${r1}")

      val r2 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map("script"->"{SCRIPT_CODE}"))))
      info(s"r2 = ${r2}")

      // s1 should !== (s2)

      we.stop(wf1.get)
    }

    "create RuntimeActors Workflow with 2 Logs" ignore {
      implicit val we = new WorkflowEngine(runtime = new RuntimeActors())

      val w1 = Workflow("wf-1",ExecData.empty,
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
      
      val wf1 = we.spawn(w1)
      info(s"wf = ${wf1}")

      we.start(wf1.get)

      Thread.sleep(100L)

      val r2 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map("script"->"{SCRIPT_CODE}"))))
      info(s"r2 = ${r2}")

      // s1 should !== (s2)

      we.stop(wf1.get)
    }
    
  }
}
