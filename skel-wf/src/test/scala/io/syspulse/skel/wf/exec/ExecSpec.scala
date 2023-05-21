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

class ExecSpec extends AnyWordSpec with Matchers with WorkflowTestable {
  
  "Exec" should {    
    """run ScriptExec with input=10 -> output=11 """ in {
      val e = new ScriptExec("script-exec-0","",dataExec = Map("script"->""" {input} + 1 """))
      val r = e.exec("in-0",ExecData(Map("input"->10)))
      r should === (Success(ExecDataEvent(ExecData(Map("input" -> 10, "output" -> 11)))))
    }

    """run ScriptExec with usjon extract id = 'X-001' """ in {
      val e = new ScriptExec("script-exec-1","",dataExec = Map("script"->""" ujson.read({input}).obj("id").str """))
      val input = """ "{ \\"id\\":\\"X-001\\",\\"v\\":100 }" """
      val r = e.exec("in-0",ExecData(Map("input"->input)))
      info(s"r = ${r}")
      r should === (Success(ExecDataEvent(ExecData(Map("input" -> input, "output" -> "X-001")))))
    }

    """run ScriptExec with Compile error """ in {
      val e = new ScriptExec("script-exec-2","",dataExec = Map("script"->""" v = 1 """))
      val r = e.exec("in-0",ExecData(Map("input"->10)))
      info(s"r = ${r}")
      r.isInstanceOf[Failure[_]] should === (true)
    }
  }
}
