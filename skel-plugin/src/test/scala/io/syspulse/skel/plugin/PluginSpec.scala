package io.syspulse.skel.plugin

import scala.util.{Try,Success,Failure}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin.store._


class PluginSpec extends AnyWordSpec with Matchers with PluginTestable {
  
  "Plugin" should {    

    """create TestPlugin instance from Plugin""" in {
      val pe = new PluginEngine(new PluginStoreClasspath())

      val r = pe.spawn(Plugin(
        name = "TestPlugin",
        typ = "class",
        init = "io.syspulse.skel.plugin.TestPlugin"
      ))

      info(s"r = ${r}")

      r shouldBe a[Success[_]]
    }

    """create TestPlugin instance from Classpath""" in {
      val pe = new PluginEngine(new PluginStoreClasspath())

      val rr = pe.spawn()
      
      info(s"rr = ${rr}")

      rr.size should !== (0)
      rr(0) shouldBe a[Success[_]]
    }
  }
}
