package io.syspulse.skel.wf.registry

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
import io.syspulse.skel.wf._

class RegistrySpec extends AnyWordSpec with Matchers with WorkflowTestable {
  
  "Registry" should {

    "Resolve Exec by Name" in {
      val registry = new WorkflowRegistry(Seq(Exec("Test","io.syspulse.skel.wf.exec.TestExec")))
      
      registry.resolve("Log") should === (Some(Exec("Log","io.syspulse.skel.wf.exec.LogExec",Seq(),Seq())))      
    }

    "Resolve Exec by Class" in {
      val registry = new WorkflowRegistry(Seq(Exec("Test","io.syspulse.skel.wf.exec.TestExec")))
      
      registry.resolve("io.syspulse.skel.wf.exec.LogExec") should === (Some(Exec("Log","io.syspulse.skel.wf.exec.LogExec",Seq(),Seq())))      
    }

    "Resolve Additional Exec by Name and Class" in {
      val registry = new WorkflowRegistry(Seq(Exec("Test","io.syspulse.skel.wf.exec.TestExec")))
      
      registry.resolve("io.syspulse.skel.wf.exec.TestExec") should === (Some(Exec("Test","io.syspulse.skel.wf.exec.TestExec",Seq(),Seq())))      
      registry.resolve("Test") should === (Some(Exec("Test","io.syspulse.skel.wf.exec.TestExec",Seq(),Seq())))      
    }

    "Assembly Exec by names and classpath" in {
      implicit val registry = new WorkflowRegistry(Seq(Exec("Test","io.syspulse.skel.wf.exec.TestExec")))

      implicit val we = new WorkflowEngine(new WorkflowStoreDir(wfDir),new WorkflowStateStoreDir(runtimeDir),new RuntimeThreads(), s"dir://${runtimeDir}")

      val w1 = Workflow.assemble("wf-001","Workflow-001","F-1(Log())->F-2(io.syspulse.skel.wf.exec.TestExec())")
      
      val wf1 = we.spawn(w1.get)
      
      wf1 shouldBe a[Success[_]]
    }

  }
}
