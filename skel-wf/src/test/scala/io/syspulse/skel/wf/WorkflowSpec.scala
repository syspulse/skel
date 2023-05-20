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

class WorkflowSpec extends AnyWordSpec with Matchers with WorkflowTestable {
  
  "Workflow" should {
    "Save workflow to StoreDir" in {      
      val w1 = Workflow("wf-1","Workflow-1",Map("file" -> "/tmp/file-10.log","time" -> 10000),
        execs = Seq(
          Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0")),data = Some(Map("sys"->"\u220e".repeat(1)))),
          Exec("F-2","io.syspulse.skel.wf.exec.TestExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("err-0"))),
          Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
        ),
        links = Seq(
          Link("link-1","F-1","out-0","F-2","in-0"),
          Link("link-2","F-2","out-0","F-3","in-0")
        )
      )
            
      val store = new WorkflowStoreDir(wfDir)
      val r = store.+(w1)
      info(s"${r}")

      r should === (Success(store))
    }

    "Load workflow from StoreDir" in {
      val w1 = Workflow("wf-2","Workflow-2",Map("file" -> "/tmp/file-10.log","time" -> 10000),
        execs = Seq(
          Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))),
          Exec("F-2","io.syspulse.skel.wf.exec.TestExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("err-0"))),
          Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")),out = Seq(Out("err-0"))),
        ),
        links = Seq(
          Link("link-1","F-1","out-0","F-2","in-0"),
          Link("link-2","F-2","out-0","F-3","in-0")
        )
      )
      
      val store = new WorkflowStoreDir(wfDir)
      
      val r = store.+(w1)
      r should === (Success(store))

      val w2 = store.?("wf-2")

      info(s"w2=${w2}")

      w2 should === (Success(w1))
    }

    "Build workflow dynamically: add F1,F2,F1->F2" in {
      val wf1 = for {
        w1 <- Success(Workflow("wf-3",""))
        w2 <- w1.update(Some("Workflow-3"))
        w3 <- w2.addData(Map("file" -> "file-1.log"))
        w4 <- w3.addData(Map("time" -> 2000))
        w5 <- w4.addExec(Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
        w6 <- w5.addExec(Exec("F-2","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")), out = Seq()))
        w7 <- w6.addLink(Link("link-1","F-1","out-0","F-2","in-0"))
      } yield w7
      
      wf1 shouldBe a[Success[_]]

      wf1.get.name should === ("Workflow-3")
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
      wf1.get.links(0) should === (Link("link-1","F-1","out-0","F-2","in-0"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-3")

      //info(s"wf2=${wf2}")

      wf2 shouldBe a[Success[_]]
    }

    "Build workflow dynamically: Linking: F1->F2" in {
      
      val wf1 = for {
        w4 <- Success(Workflow("wf-4","Workflow-4"))

        w5 <- w4.addExec(Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
        w6 <- w5.addExec(Exec("F-2","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")), out = Seq()))
        l1 <- w6.linkExecs(w6.execs(0),w6.execs(1))
        w7 <- w6.addLink(l1)
      } yield w7
      
      wf1 shouldBe a[Success[_]]

      wf1.get.name should === ("Workflow-4")
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
      wf1.get.links(0) should === (Link("F-1:out-0---F-2:in-0","F-1","out-0","F-2","in-0"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-4")

      //info(s"wf2=${wf2}")

      wf2 shouldBe a[Success[_]]
    }

    "Build workflow dynamically: add F1,F2,F1->F2, del F2" in {
      val wf1 = for {
        w4 <- Success(Workflow("wf-5",""))

        w5 <- w4.addExec(Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
        w6 <- w5.addExec(Exec("F-2","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")), out = Seq()))
        w7 <- w6.addLink(Link("link-1","F-1","out-0","F-2","in-0"))
        w8 <- w7.delExec("F-1")
      } yield w7
      
      wf1 shouldBe a[Success[_]]

      wf1.get.execs(0) should === (Exec("F-2","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")), out = Seq()))
      wf1.get.links.size should === (0)
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-5")

      info(s"wf2=${wf2}")

      wf2 shouldBe a[Success[_]]
    }

    "Build workflow dynamically from DSL: F1(Log)->F2(Log)->F3(Terminate)" in {
      val wf1 = Workflow.assemble("wf-7","Workflow-7","F-1(LogExec(sys=1,log.level=WARN))->F-2(LogExec(sys=2))->F-3(TerminateExec())")
      
      info(s"wf1=${wf1}")

      wf1 shouldBe a[Success[_]]

      wf1.get.execs.size should === (3)
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0")),data = Some(Map("sys"->"1","log.level"->"WARN"))))
      wf1.get.execs(1) should === (Exec("F-2","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0")),data = Some(Map("sys"->"2"))))
      wf1.get.execs(2) should === (Exec("F-3","io.syspulse.skel.wf.exec.TerminateExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
      wf1.get.links.size should === (2)
      wf1.get.links(0) should === (Link("F-1:out-0---F-2:in-0","F-1","out-0","F-2","in-0"))
      wf1.get.links(1) should === (Link("F-2:out-0---F-3:in-0","F-2","out-0","F-3","in-0"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-7")

      info(s"wf2=${wf2}")

      wf2 shouldBe a[Success[_]]
    }

    "Build workflow from DSL: F-1(LogExec())->F-1[in-0]()" in {
      val wf1 = Workflow.assemble("wf-8","Workflow-8","F-1(LogExec())->F-1[in-0]()")
      
      wf1 shouldBe a[Success[_]]

      wf1.get.execs.size should === (1)
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
      wf1.get.links.size should === (1)
      wf1.get.links(0) should === (Link("F-1:out-0---F-1:in-0","F-1","out-0","F-1","in-0"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-8")
      
      wf2 shouldBe a[Success[_]]
    }

    "Build workflow from DSL: F-1(LogExec)->F-1[in-0]" in {
      val wf1 = Workflow.assemble("wf-9","Workflow-9","F-1(LogExec)->F-1[in-0]")
      
      wf1 shouldBe a[Success[_]]

      wf1.get.execs.size should === (1)
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"))))
      wf1.get.links.size should === (1)
      wf1.get.links(0) should === (Link("F-1:out-0---F-1:in-0","F-1","out-0","F-1","in-0"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-9")
      
      wf2 shouldBe a[Success[_]]
    }

    "Build workflow from DSL: F-1(LogExec(v=1,v=2))->F-1[in-0]" in {
      val wf1 = Workflow.assemble("wf-10","Workflow-10","F-1(LogExec(v1=1,v2=2))->F-1[in-0]")
      
      wf1 shouldBe a[Success[_]]

      wf1.get.execs.size should === (1)
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0")), data = Some(Map("v1"->"1","v2"->"2"))))
      wf1.get.links.size should === (1)
      wf1.get.links(0) should === (Link("F-1:out-0---F-1:in-0","F-1","out-0","F-1","in-0"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-10")
      
      wf2 shouldBe a[Success[_]]
    }

    "Build workflow with Loop: F-1[in-2](LogExec(v1=1,v2=2))[out-2]->F-1[in-2]" in {
      val wf1 = Workflow.assemble("wf-11","Workflow-11","F-1[in-2](LogExec(v1=1,v2=2))[out-2]->F-1[in-2]")
      
      wf1 shouldBe a[Success[_]]

      wf1.get.execs.size should === (1)
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-2")), out = Seq(Out("out-2"),Out("out-0")), data = Some(Map("v1"->"1","v2"->"2"))))
      wf1.get.links.size should === (1)
      wf1.get.links(0) should === (Link("F-1:out-2---F-1:in-2","F-1","out-2","F-1","in-2"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-11")
      
      wf2 shouldBe a[Success[_]]
    }

    "Build workflow with 2 outputs: " in {
      val dsl = """
      F-1(LogExec())[out-0]->F-2[in-0](LogExec())
      F-1()[out-1]->F-2[in-1]
      """
      
      val wf1 = Workflow.assemble("wf-12","Workflow-12",dsl)
      //info(s"${wf1.get}")
      
      wf1 shouldBe a[Success[_]]

      wf1.get.execs.size should === (2)
      wf1.get.execs(0) should === (Exec("F-1","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0")), out = Seq(Out("out-0"),Out("out-1")), data = None))
      wf1.get.execs(1) should === (Exec("F-2","io.syspulse.skel.wf.exec.LogExec",in = Seq(In("in-0"),In("in-1")), out = Seq(Out("out-0")), data = None))
      
      wf1.get.links.size should === (2)
      wf1.get.links(0) should === (Link("F-1:out-0---F-2:in-0","F-1","out-0","F-2","in-0"))
      wf1.get.links(1) should === (Link("F-1:out-1---F-2:in-1","F-1","out-1","F-2","in-1"))
                  
      val store = new WorkflowStoreDir(wfDir)      

      val r = store.+(wf1.get)
      r should === (Success(store))
      val wf2 = store.?("wf-12")
      
      wf2 shouldBe a[Success[_]]
    }
    
  }
}
