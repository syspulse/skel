package io.syspulse.skel.dsl

import scala.tools.nsc.interpreter._

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

import com.typesafe.scalalogging.Logger
import scala.tools.nsc.Settings

class ScalaInterpreter() {
  val log = Logger(s"${this}")
   
  val engine = scala.tools.nsc.interpreter.shell.Scripted()

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.info(s"args=${args}, script=${script}, engine=${engine}")

    val r = engine.eval(script)    
    log.debug(s"r=${r}")
    r
  }
}