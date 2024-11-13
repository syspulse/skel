package io.syspulse.skel.test

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._

trait TestUtil {

  def readResources(path:String):String = {
    val jsonResource = getClass.getResourceAsStream(s"/$path")
    val jsonContent = scala.io.Source.fromInputStream(jsonResource).mkString
    jsonContent
  }
}