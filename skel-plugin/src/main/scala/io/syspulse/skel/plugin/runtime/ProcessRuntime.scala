package io.syspulse.skel.plugin.runtime

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class ProcessRuntime(p:PluginDescriptor) extends PluginRuntime {
  val log = Logger(s"${this}")
  
  def spawn(p:PluginDescriptor):Try[Plugin] = {
    Success(new Running(p.init))
  }

  class Running(script:String) extends Plugin {
    
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

    override def pluginStart():Try[Any] = run(Map())
    override def pluginStop():Try[Any] =  Success(this)
    override def pluginId():String = p.init
  }

}
