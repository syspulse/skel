package io.syspulse.skel.dsl

import scala.tools.nsc.interpreter._

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

import com.typesafe.scalalogging.Logger
import scala.tools.nsc.Settings

class ScalaInterpreter() {
  val log = Logger(s"${this}")
 
  val settings = new Settings()//.withErrorFn((s) => {println(s"Error: ${s}")})
  settings.usejavacp.value = true
  val reporter = new shell.ReplReporterImpl(settings)

  val engine = new IMain(settings,reporter)

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.debug(s"args=${args}, script=${script}")

    // val param1 = "str"
    // val param2 = 10    
    // val r = engine.interpret(s"val param1 = $param1; val param2 = $param2; $script")    

    val r = engine.interpretSynthetic("")
    log.debug(s"r=${r}")
    r
  }
}