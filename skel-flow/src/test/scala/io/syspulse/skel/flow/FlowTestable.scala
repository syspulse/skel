package io.syspulse.skel.flow

import scala.util.{Try,Success,Failure}

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import os._

trait FlowTestable {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  val flowDir = "/tmp/Flow1"
  os.makeDir.all(os.Path("/tmp/Flow1"))

  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.0001)

}
