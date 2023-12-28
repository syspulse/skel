package io.syspulse.skel.dsl

import com.typesafe.scalalogging.Logger
import javax.script._

import scala.jdk.CollectionConverters._

class ScalaScript()  {
  val log = Logger(s"${this}")

  // scala tests are not working in sbt ->
  // java.lang.AssertionError: assertion failed:                                                                        
  // No RuntimeVisibleAnnotations in classfile with ScalaSignature attribute: class Predef
  val engine = new ScriptEngineManager().getEngineByName("scala").asInstanceOf[ScriptEngine with Compilable]

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.info(s"script=${script}, engine=$engine")
    
    //val bind = engine.createBindings();
    val bind = new SimpleBindings()
    args.foreach{ case(n,v) => bind.put(n, v) }
    engine.setBindings(bind, ScriptContext.ENGINE_SCOPE)

    log.info(s"args=${args}: bind=${bind.entrySet().asScala.toSeq}")

    val result = engine.compile(script).eval(bind)
    
    result
  }
  
}