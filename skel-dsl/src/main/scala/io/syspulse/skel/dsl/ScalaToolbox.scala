package io.syspulse.skel.dsl

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

import com.typesafe.scalalogging.Logger

class ScalaToolbox(src:Option[String]=None) {
  val log = Logger(s"${this}")
 
  // TO compile and run code we will use a ToolBox api.
  val engine = currentMirror.mkToolBox()

  val compiled = src.map(src => {
    val q = engine.parse(src)
    log.info(s"q=${q}")
    engine.compile(q)
  })

  def run():Option[Any] = {
    log.info(s"script=${compiled}")
    compiled.map(c => c())
  }

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    // log.info(s"args=${args}, script=${script}")
    val src = args.foldLeft(script.toString){ case(s,(name,v)) => {
      //s.replaceAll(s"\\{${name}\\}",v.toString)
      s.replace(s"{${name}}",v.toString)
    }}
    log.info(s"args=${args}, script=${script}, src=${src}")
    val q = engine.parse(src)
    log.info(s"q=${q}")
    val r = engine.compile(q)()
    log.info(s"r=${r}")
    r
  }
}