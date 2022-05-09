package io.syspulse.skel.stream

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.dsl.JS
import akka.stream.scaladsl.Flow
import scala.util.Try
import scala.util.Success
import scala.util.Failure

case class Config(
  script:String="",
  className:String="",
  cmd:Seq[String] = Seq()
)

object AppStreamStd extends {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  
  def spawn(className:String):Try[StreamStd] = {
    try {
      Success(this.getClass.getClassLoader.loadClass(className).getDeclaredConstructor().newInstance().asInstanceOf[StreamStd])
    }catch {
      case e:Exception => {
        Failure(e)
      }
    }
  }


  def main(args:Array[String]) = {
    
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"stream-std","",
        ArgString('s', "script","Javascript script"),
        ArgString('c', "class.name","Classname of streamer (def: io.syspulse.skel.stream.StreamStd)"),
        ArgParam("<cmd>","commands ('write','read') (def: write)")
      )
    ))
    
    val config = Config(
      script = c.getString("script").getOrElse(""),
      className = c.getString("class.name").getOrElse("io.syspulse.skel.stream.StreamStd"),
      cmd = c.getParams()
    )

    
    val stream = spawn(config.className)
    if(stream.isFailure) System.exit(2)

    log.info(s"class=${stream.get.getClass().getName()}")
    
    stream.get.withConfig(c).run(None)
    //new StreamStd().withConfig(c).run(scriptFlow)
  }
}

