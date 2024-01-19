package io.syspulse.skel.plugin

import scala.util.{Try,Success,Failure}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import os._

trait PluginTestable {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  val pluginDir = "/tmp/skel-plugin/test/plugins"
    
  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.0001)

}
