
package io.syspulse.skel.dsl

import com.typesafe.scalalogging.Logger
import javax.script.ScriptEngineManager

abstract class ScriptEngine(lang:String) {
  val log = Logger(s"${this}")
      
  def run(script:String,args:Map[String,Any] = Map()):Any
}