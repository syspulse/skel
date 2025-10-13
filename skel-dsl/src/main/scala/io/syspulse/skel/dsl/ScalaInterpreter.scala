package io.syspulse.skel.dsl

import scala.tools.nsc.interpreter._
import scala.tools.nsc.interpreter.shell.Scripted

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

import com.typesafe.scalalogging.Logger
import scala.tools.nsc.Settings
import javax.script.{ScriptContext, SimpleScriptContext}
import java.io.PrintWriter

class ScalaInterpreter() {
  val log = Logger(s"${this}")
   
  val engine = scala.tools.nsc.interpreter.shell.Scripted()

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.info(s"args=${args}, script=${script}, engine=${engine}")

    // Create a proper ScriptContext
    val ctx = new SimpleScriptContext()
    
    // Set context variables that can be accessed in the script
    args.foreach { case (name, value) =>
      ctx.setAttribute(name, value, ScriptContext.ENGINE_SCOPE)
    }

    val r = engine.eval(script, ctx)    
    log.debug(s"r=${r}")
    r
  }
}