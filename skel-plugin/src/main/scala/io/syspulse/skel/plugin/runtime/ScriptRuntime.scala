package io.syspulse.skel.plugin.runtime

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._
import io.syspulse.skel.dsl.ScalaScript
//import io.syspulse.skel.dsl.ScalaToolbox

class ScriptRuntime(p:Plugin) extends PluginRuntime {
  val log = Logger(s"${this}")
  
  def spawn(p:Plugin):Try[Runtime[String]] = {
    Success(new Running(p.init))
  }
  
  class Running(script:String) extends Runtime[String] {
    //val engine = new ScalaToolbox()
    val engine = new ScalaScript()
    
    def run(data:Map[String,Any]):Try[String] = {
      val script = ""
      log.info(s"script='${script}'")
      val src = if(script.toString.startsWith("file://")) 
        os.read(os.Path(script.toString.stripPrefix("file://"),os.pwd))
      else
        script.toString

      val r = try {
        val output = engine.run(src,data)
        Success(s"${output}")

      } catch {
        case e:Throwable => 
          log.error(s"script failed",e)
          Failure(e)
      }
                
      log.info(s"r = ${r}")
      r
    }

    def pluginStart():Try[Any] = run(Map())
    def pluginStop():Try[Any] =  Success(this)

    def pluginId():Try[String] = Success(this.toString)
  }
}
