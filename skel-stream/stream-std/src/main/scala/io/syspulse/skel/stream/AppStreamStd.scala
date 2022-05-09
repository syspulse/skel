package io.syspulse.skel.stream

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.dsl.JS
import akka.stream.scaladsl.Flow

case class Config(
  script:String="",
  cmd:Seq[String] = Seq()
)

object AppStreamStd extends {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  
  def main(args:Array[String]) = {
    
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"tool-keystore","",
        ArgString('s', "script","Javascript script"),
        
        ArgParam("<cmd>","commands ('write','read') (def: write)")
      )
    ))
    
    val config = Config(
      script = c.getString("script").getOrElse(""),
      cmd = c.getParams()
    )

    val scriptFlow = config.script match {
      case "" => None
      case script => { 
        val js = new JS(script)
        Some(
          Flow.fromFunction( 
            (s:String) => js.run(Map( ("input" -> s) )).toString()
          )
        )
      }
    }
    
    new StreamStd(config).run(scriptFlow)
  }
}

