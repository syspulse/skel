package io.syspulse.skel.test

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._
import java.io.File

trait TestUtil {
  // Get the project module directory
  def projectDir = new File(".").getAbsolutePath
  
  // Get test resources directory
  def testDir = this.getClass.getClassLoader.getResource(".").getPath 
  
  def readResources(path:String):String = {
    val jsonResource = getClass.getResourceAsStream(s"/$path")
    val jsonContent = scala.io.Source.fromInputStream(jsonResource).mkString
    jsonContent
  }
}