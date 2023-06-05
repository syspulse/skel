
package io.syspulse.skel.dsl

import com.typesafe.scalalogging.Logger
import javax.script.ScriptEngineManager

abstract class ScriptEngine(lang:String) {
  val log = Logger(s"${this}")
  val engine = new ScriptEngineManager(this.getClass().getClassLoader()).getEngineByName(lang);

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.info(s"args=${args}, script=${script}, engine=$engine")

    val bind = engine.createBindings();
    args.foreach{ case(n,v) => bind.put(n, v) }
    val result = engine.eval(script, bind)

    result
  }
}