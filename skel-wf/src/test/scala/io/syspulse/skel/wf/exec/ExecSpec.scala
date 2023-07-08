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
import io.syspulse.skel.wf.exec.TestExec
import io.syspulse.skel.wf.exec.ScriptExec
import io.syspulse.skel.wf.exec.SplitExec

class ExecSpec extends AnyWordSpec with Matchers with WorkflowTestable {
  
  "Exec" should {    
    """run ScriptExec with input=10 -> output=11 """ in {
      val e = new ScriptExec("script-exec-0","",dataExec = Map("script"->""" {input} + 1 """))
      val r = e.exec("in-0",ExecData(Map("input"->10)))
      //r should === (Success(ExecDataEvent(ExecData(Map("input" -> 10, "output" -> 11)))))
      r should === (Success(ExecDataEvent(ExecData(Map("input" -> 11)))))
    }

    """run ScriptExec with usjon extract id = 'X-001' """ in {
      val e = new ScriptExec("script-exec-1","",dataExec = Map("script"->""" ujson.read({input}).obj("id").str """))
      val input = """ "{ \\"id\\":\\"X-001\\",\\"v\\":100 }" """
      val r = e.exec("in-0",ExecData(Map("input"->input)))
      info(s"r = ${r}")
      //r should === (Success(ExecDataEvent(ExecData(Map("input" -> input, "output" -> "X-001")))))
      r should === (Success(ExecDataEvent(ExecData(Map("input" -> "X-001")))))
    }

    """run ScriptExec with Compile error """ in {
      val e = new ScriptExec("script-exec-2","",dataExec = Map("script"->""" v = 1 """))
      val r = e.exec("in-0",ExecData(Map("input"->10)))
      info(s"r = ${r}")
      r.isInstanceOf[Failure[_]] should === (true)
    }

    """run SplitExec with input='1,,2,3' -> output=[1,2,3] """ in {
      val e = new SplitExec("split-exec-3","",dataExec = Map("splitter"->","))
      val r = e.exec("in-0",ExecData(Map("input"->"1,,2,3")))
      r should === (Success(ExecDataEvent(ExecData(Map("input" -> "1,2,3","input.size"->3)))))
    }

    """run SplitExec with input='1,,2,3' -> output=[1,,2,3] with empty""" in {
      val e = new SplitExec("split-exec-3","",dataExec = Map("splitter"->",","empty"->"true"))
      val r = e.exec("in-0",ExecData(Map("input"->"1,,2,3")))
      r should === (Success(ExecDataEvent(ExecData(Map("input" -> "1,,2,3","input.size"->4)))))
    }

    """run SplitExec with input='1\n \n2\n3' -> output=[1,2,3] """ in {
      val e = new SplitExec("split-exec-3","",dataExec = Map())
      val r = e.exec("in-0",ExecData(Map("input"->"1\n \r\n2\n3")))
      r should === (Success(ExecDataEvent(ExecData(Map("input" -> "1,2,3","input.size"->3)))))
    }
  }
}
