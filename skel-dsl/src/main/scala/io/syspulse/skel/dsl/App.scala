package io.syspulse.skel.dsl

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(  
  cmd:String = "scala",
  params: Seq[String] = Seq(),
)

object App  {

  def main(args:Array[String]):Unit = {
    Console.err.println(s"args(${args.size}): '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-dsl","",
        
        ArgCmd("js","JavaScript ScriptEngine script"),
        ArgCmd("scala","Scala ScriptEngine script"),
        ArgCmd("scala-script","Scala ScriptEngine"),
        ArgCmd("scala-interpreter","Scala interpreter script"),
        ArgCmd("scala-toolbox","Scala Toolbox script"),
        
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val r = config.cmd match {
      case "js" =>
        new JS().run(config.params.mkString(" "))

      case "scala-script" =>
        new SCALA().run(config.params.mkString(" "))

      case "scala" =>
        new ScalaScript().run(config.params.mkString(" "),Map("i"->Util.generateRandomToken(None,sz=64)))

      case "scala-toolbox" =>
        new ScalaToolbox().run(config.params.mkString(" "))

      case "scala-interpreter" =>
        //scala.tools.nsc.interpreter.shell.Scripted().eval(config.params.mkString(" "))
        new ScalaInterpreter().run(config.params.mkString(" "))

      case "scala-imain" =>
        new ScalaIMain().run(config.params.mkString(" "))
      
      case _ => 
        Console.err.println(s"unknown Script Enginer: ${config.cmd}")
        sys.exit(1)
    }
    Console.err.println(s"r = ${r}")
  }
}

