package io.syspulse.skel.wf

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import os._

trait WorkflowTestable {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  val wfDir = "/tmp/skel-wf/test/workflows"
  val storeDir = "/tmp/skel-wf/test/runtime"
  os.makeDir.all(os.Path("/tmp/skel-wf/test/wf-1"))

  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.0001)

}
