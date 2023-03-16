package io.syspulse.skel.wf

import scala.util.{Try,Success,Failure}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf.runtime.thread._
import io.syspulse.skel.wf.runtime.actor.RuntimeActors
import io.syspulse.skel.wf.store.WorkflowStoreDir
import io.syspulse.skel.wf.store.WorkflowStateStoreDir

class WorkflowEngineSpec extends AnyWordSpec with Matchers with WorkflowTestable {
  
  "WorkflowEngine" should {
    
    // "run RuntimeThreads Workflow and stop it externally" in {
    //   implicit val we = new WorkflowEngine(new WorkflowStoreDir(wfDir),new WorkflowStateStoreDir(runtimeDir),new RuntimeThreads(), s"dir://${runtimeDir}",testRegistry)

    //   val w1 = Workflow("wf-3","wf-3",Map(),
    //     execs = Seq(
    //       Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0")),data = Some(Map("sys"->"\u220e".repeat(1)))),
    //       Exec("F-2","io.syspulse.skel.wf.exec.TestExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("err-0"))),
    //       // Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
    //     ),
    //     links = Seq(
    //       Link("link-1","F-1","out-0","F-2","in-0"),
    //       // Link("link-2","F-2","out-0","F-3","in-0")
    //     )
    //   )
      
    //   //info(s"w1 = ${w1}")      
      
    //   val wf1 = we.spawn(w1)
    //   //info(s"wf = ${wf1}")

    //   we.start(wf1.get)

    //   Thread.sleep(100L)
      
    //   val r2 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map("script"->"{SCRIPT_CODE}"))))
    //   //info(s"r2 = ${r2}")

    //   Thread.sleep(140L)
    //   wf1.get.state.status should === (WorkflowState.STATUS_RUNNING)
      
    //   we.stop(wf1.get)

    //   wf1.get.state.status should === (WorkflowState.STATUS_STOPPED)
    // }
    
    // "run RuntimeThreads Workflow and it should terminate by itself" in {
    //   implicit val we = new WorkflowEngine(new WorkflowStoreDir(wfDir),new WorkflowStateStoreDir(runtimeDir),new RuntimeThreads(), s"dir://${runtimeDir}",testRegistry)

    //   val w1 = Workflow("wf-4","wf-4",Map(),
    //     execs = Seq(
    //       Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))),
    //       Exec("F-2","io.syspulse.skel.wf.exec.TestExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("err-0"))),
    //       Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
    //     ),
    //     links = Seq(
    //       Link("link-1","F-1","out-0","F-2","in-0"),
    //       Link("link-2","F-2","out-0","F-3","in-0")
    //     )
    //   )
      
    //   // info(s"w1 = ${w1}")      
      
    //   val wf1 = we.spawn(w1)
    //   // info(s"wf = ${wf1}")

    //   we.start(wf1.get)

    //   Thread.sleep(100L)
      
    //   val r2 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map("script"->"{SCRIPT_CODE}"))))
    //   info(s"r2 = ${r2}")

    //   Thread.sleep(100L)

    //   wf1.get.state.status should === (WorkflowState.STATUS_STOPPED)
    // }

    // "create RuntimeActors Workflow with 2 Logs" in {
    //   implicit val we = new WorkflowEngine(new WorkflowStoreDir(wfDir),new WorkflowStateStoreDir(runtimeDir),new RuntimeActors(), s"dir://${runtimeDir}",testRegistry)
      
    //   val w1 = Workflow("wf-5","wf-5",Map(),
    //     execs = Seq(
    //       Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))),
    //       Exec("F-2","io.syspulse.skel.wf.exec.TestExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("err-0"))),
    //       Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
    //     ),
    //     links = Seq(
    //       Link("link-1","F-1","out-0","F-2","in-0"),
    //       Link("link-2","F-2","out-0","F-3","in-0")
    //     )
    //   )
      
    //   info(s"w1 = ${w1}")      
      
    //   val wf1 = we.spawn(w1)
    //   info(s"wf = ${wf1}")

    //   we.start(wf1.get)

    //   Thread.sleep(100L)

    //   val r2 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map("script"->"{SCRIPT_CODE}"))))
    //   info(s"r2 = ${r2}")

    //   // s1 should !== (s2)

    //   we.stop(wf1.get)
    // }

    // "run Workflow cron " in {
    //   implicit val we = new WorkflowEngine(new WorkflowStoreDir(wfDir),new WorkflowStateStoreDir(runtimeDir),new RuntimeThreads(), s"dir://${runtimeDir}",testRegistry)

    //   val w1 = Workflow("wf-6","wf-6",Map(),
    //     execs = Seq(
    //       Exec("F-1","io.syspulse.skel.wf.exec.CronExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("out-1")),data = Some(Map("cron"->"1000"))),
    //       Exec("F-2","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(),data = Some(Map("sys"->"\u220e".repeat(1)))),
    //       Exec("F-3","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(),data = Some(Map("sys"->"\u2b25".repeat(1)))),
    //     ),
    //     links = Seq(
    //       Link("link-1","F-1","out-0","F-2","in-0"),
    //       Link("link-2","F-1","out-1","F-3","in-0")
    //     )
    //   )
    //   val wf1 = we.spawn(w1)
    //   Thread.sleep(100L)
    //   we.start(wf1.get)
   
    //   Thread.sleep(100L)
    //   wf1.get.state.status should === (WorkflowState.STATUS_RUNNING)


    //   Thread.sleep(3000L)
    //   we.stop(wf1.get)

    //   wf1.get.state.status should === (WorkflowState.STATUS_STOPPED)
    // }

    // "run Workflow with ProcessExec" in {
    //   implicit val we = new WorkflowEngine(new WorkflowStoreDir(wfDir),new WorkflowStateStoreDir(runtimeDir),new RuntimeThreads(), s"dir://${runtimeDir}",testRegistry)
      
    //   val script = "ls -l {dir}"

    //   val w1 = Workflow("wf-20","wf-20",Map(),
    //     execs = Seq(
    //       Exec("F-1","io.syspulse.skel.wf.exec.ProcessExec",in = Seq(In("in-0")), out = Seq(Out("out-0")),Some(Map("script" -> script))),
    //       Exec("F-2","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("err-0"))),
    //       Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
    //     ),
    //     links = Seq(
    //       Link("link-1","F-1","out-0","F-2","in-0"),
    //       Link("link-2","F-2","out-0","F-3","in-0")
    //     )
    //   )
           
    //   val wf1 = we.spawn(w1)
    //   info(s"wf = ${wf1}")

    //   we.start(wf1.get)
    //   Thread.sleep(100L)

    //   val r2 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map("dir"->"/"))))
    //   info(s"r2 = ${r2}")

    //   // s1 should !== (s2)

    //   we.stop(wf1.get)
    // }

    "run Workflow with RandExec + SeqExec" in {
      implicit val we = new WorkflowEngine(new WorkflowStoreDir(wfDir),new WorkflowStateStoreDir(runtimeDir),new RuntimeThreads(), s"dir://${runtimeDir}",testRegistry)
      
      val w1 = Workflow("wf-21","wf-21",Map(),
        execs = Seq(
          Exec("F-1","io.syspulse.skel.wf.exec.RandExec",in = Seq(In("in-0")), out = Seq(Out("out-0")),Some(Map("rand.num" -> 10))),
          Exec("F-2","io.syspulse.skel.wf.exec.SeqExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))),
          Exec("F-3","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))),
          // Exec("F-4","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
        ),
        links = Seq(
          Link("link-1","F-1","out-0","F-2","in-0"),
          Link("link-2","F-2","out-0","F-3","in-0"),
          // Link("link-3","F-3","out-0","F-4","in-0")
        )
      )
           
      val wf1 = we.spawn(w1)
      info(s"wf = ${wf1}")

      we.start(wf1.get)
      Thread.sleep(100L)

      val r2 = wf1.get.emit("F-1","in-0",ExecDataEvent(ExecData(Map())))
      info(s"r2 = ${r2}")

      Thread.sleep(250L)
      // s1 should !== (s2)

      we.stop(wf1.get)
    }

  }
}
