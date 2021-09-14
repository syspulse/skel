package io.syspulse.skel.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import os._

trait FlowTestable {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  val flowDir = "/tmp/NPP1"
  os.makeDir.all(os.Path("/tmp/NPP1"))

  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.0001)
}
