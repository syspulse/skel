
package io.syspulse.skel.dsl

import com.typesafe.scalalogging.Logger
import javax.script.ScriptEngineManager

class JS(script:String) {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  val engine = new ScriptEngineManager().getEngineByName("nashorn");
  
  def run(args:Map[String,Any] = Map()):Any = {
    log.debug(s"args=${args}, script=${script}")

    val bind = engine.createBindings();
    args.foreach{ case(n,v) => bind.put(n, v) }
    val result = engine.eval(script, bind)

    result
  }
}