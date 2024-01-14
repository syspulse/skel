package io.syspulse.skel.plugin.runtime

import scala.util.{Try,Success,Failure}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin.store.PluginStoreDir
import io.syspulse.skel.plugin.PluginTestable

class RuntimeSpec extends AnyWordSpec with Matchers with PluginTestable {
  
  "Runtime" should {    
    // """run ScriptExec with input=10 -> output=11 """ in {
    //   val e = new ScriptExec("script-runtime-0","",dataExec = Map("script"->""" {input} + 1 """))
    //   val r = e.runtime("in-0",ExecData(Map("input"->10)))
    //   //r should === (Success(ExecDataEvent(ExecData(Map("input" -> 10, "output" -> 11)))))
    //   r should === (Success(ExecDataEvent(ExecData(Map("input" -> 11)))))
    // }

  }
}
