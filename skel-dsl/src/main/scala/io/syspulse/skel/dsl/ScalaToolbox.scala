package io.syspulse.skel.dsl

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

import com.typesafe.scalalogging.Logger

class ScalaToolbox() {
  val log = Logger(s"${this}")
 
  // TO compile and run code we will use a ToolBox api.
  val engine = currentMirror.mkToolBox()

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.debug(s"args=${args}, script=${script}")
    val q = engine.parse(script)
    val r = engine.compile(q)()
    log.debug(s"r=${r}")
    r
  }
}