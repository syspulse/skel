
package io.syspulse.skel.dsl

import com.typesafe.scalalogging.Logger
import javax.script.ScriptEngineManager

abstract class ScriptEngineLegacy(lang:String) extends ScriptEngine(lang) {  
    
  val engine = lang.toLowerCase match {
    case "javascript" | "js" | "graal.js" =>
      // [engine] WARNING: The polyglot context is using an implementation that does not support runtime compilation.
      // The guest application code will therefore be executed in interpreted mode only.
      // Execution only in interpreted mode will strongly impact the guest application performance.
      // For more information on using GraalVM see https://www.graalvm.org/java/quickstart/.
      // To disable this warning the '--engine.WarnInterpreterOnly=false' option or use the '-Dpolyglot.engine.WarnInterpreterOnly=false' system property.
      new ScriptEngineManager(this.getClass().getClassLoader()).getEngineByName("graal.js")      
    case _ => 
      new ScriptEngineManager(this.getClass().getClassLoader()).getEngineByName(lang)
  }

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.debug(s"[${lang}] ${engine}: args=${args}, script=${script}")

    val bind = engine.createBindings();
    args.foreach{ case(n,v) => bind.put(n, v) }
    val result = engine.eval(script, bind)

    result
  }
}