package io.syspulse.skel.plugin.runtime

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class ProcessRuntime(p:Plugin) extends PluginRuntime {
  val log = Logger(s"${this}")
  
  def spawn(p:Plugin):Try[Runtime[String]] = {
    Success(new Running(p.init))
  }

  class Running(script:String) extends Runtime[String] {
    
    def run(data:Map[String,Any]):Try[String] = {      
            
      // replace {} with variables from Data
      val cmd = data.foldLeft(script.toString){ case(s,(name,v)) => {
        s.replaceAll(s"\\{${name}\\}",v.toString) 
      }}
      
      val err = new StringBuffer()
      val r = cmd lazyLines_! ProcessLogger(err append _)
      val txt = r.mkString("\n")
      log.info(s"cmd = '${cmd}' -> '${txt}'")
      
      Success(txt)
    }

    def start():Try[Any] = run(Map())

    def stop():Try[Any] =  Success(this)

    def id():Try[String] = Success(p.init)
  }

}
