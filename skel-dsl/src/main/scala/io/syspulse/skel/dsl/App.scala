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
        ArgCmd("scala-script","Scala ScriptEngine (same as `scala` with cast `-Dscala.usejavacp=true`)"),
        ArgCmd("scala-interpreter","Scala interpreter script"),
        ArgCmd("scala-toolbox","Scala Toolbox script"),

        ArgCmd("polyglot-js","GraalJS Polyglot ScriptEngine"),
        ArgCmd("polyglot-js-sandbox","GraalJS Polyglot ScriptEngine with sandbox"),
        ArgCmd("nashorn","Nashorn ScriptEngine"),
        ArgCmd("js-nashorn","Nashorn ScriptEngine"),

        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    val script = config.params.foldLeft("")((r,s) => r + {
      s.split("://").toList match {
        case "file" :: file :: Nil => os.read(os.Path(file,os.pwd)) + "\n"
        case _ => s + "\n"
      }      
    })

    Console.err.println(s"Config: ${config}")

    val defParasm = Map("i"->100,"s"->Util.generateRandomToken(None,sz=64))

    val r = config.cmd match {
      case "js" =>
        new JS().run(script,defParasm)

      case "nashorn" | "js-nashorn" =>
        new NASHORN().run(script,defParasm)

      case "polyglot-js" =>
        new Polyglot("js").run(script,defParasm)

      case "polyglot-js-sandbox" =>
        new PolyglotSandbox("js")
          .run(script,defParasm)

      case "scala" =>
        new SCALA().run(script,defParasm)

      case "scala-script" =>
        new ScalaScript().run(script,defParasm)

      case "scala-toolbox" =>
        new ScalaToolbox().run(script,defParasm)

      case "scala-interpreter" =>
        //scala.tools.nsc.interpreter.shell.Scripted().eval(config.params.mkString(" "))
        new ScalaInterpreter().run(script,defParasm)

      case "scala-imain" =>
        new ScalaIMain().run(script,defParasm)
      

      case _ => 
        Console.err.println(s"unknown Script Enginer: ${config.cmd}")
        sys.exit(1)
    }
    Console.err.println(s"r = ${r}")
  }
}

