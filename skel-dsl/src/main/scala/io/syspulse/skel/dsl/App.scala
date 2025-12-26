package io.syspulse.skel.dsl

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(  
  cmd:String = "scala",
  loop:Int = 1,
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

        ArgInt('l', "loop",s"Loop count (def: ${d.loop})"),

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
      loop = c.getInt("loop").getOrElse(d.loop),

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

    def run(e:ScriptEngine):Unit = {
      for(i <- 0 until config.loop) {
        val r = e.run(script,defParasm)
        r match {
          case scala.util.Success(result) => Console.err.println(s"r[${i}] = ${result}")
          case scala.util.Failure(e) => Console.err.println(s"r[${i}] = ERROR: ${e.getMessage}")
        }
      }
    }

    val r = config.cmd match {
      case "js" =>
        val e = new JS()
        run(e)

      case "nashorn" | "js-nashorn" =>
        run(new NASHORN())

      case "polyglot-js" =>
        run(new Polyglot("js"))

      case "polyglot-js-sandbox" =>
        run(new PolyglotSandbox("js"))

      case "scala" =>
        run(new SCALA())

      case "scala-script" =>
        for(i <- 0 until config.loop) {
          new ScalaScript().run(script,defParasm)
        }

      case "scala-toolbox" =>
        val e = new ScalaToolbox()
        for(i <- 0 until config.loop) {
          val r = e.run(script,defParasm)
          Console.err.println(s"r[${i}] = ${r}")
        }

      case "scala-interpreter" =>
        val e = new ScalaInterpreter()
        for(i <- 0 until config.loop) {
          //scala.tools.nsc.interpreter.shell.Scripted().eval(config.params.mkString(" "))
          val r = e.run(script,defParasm)
          Console.err.println(s"r[${i}] = ${r}")
        }

      case "scala-imain" =>
        val e = new ScalaIMain()
        for(i <- 0 until config.loop) {
          val r = e.run(script,defParasm)
          Console.err.println(s"r[${i}] = ${r}")
        }
      
      case _ => 
        Console.err.println(s"unknown Script Enginer: ${config.cmd}")
        sys.exit(1)
    }
    Console.err.println(s"r = ${r}")
  }
}

