package io.syspulse.skel.cron

import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv,ConfigurationProp}

import scopt.OParser
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(
  cron:String = "",
  quartz:String = "",
)

object CronApp  {

  def main(args:Array[String]):Unit = {
    Console.err.println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]("crontab.cron").action((x, c) => c.copy(cron = x)).text("cron expression (def: '*/1 * * * * *')"),
        opt[String]("crontab.quartz").action((x, c) => c.copy(quartz = x)).text("quartz config properties (def: default)"),

        arg[String]("<args>...").unbounded().optional()
          //.action((x, c) => c.copy(sinks = c.sinks :+ x)).text("Sinks"), note("" + sys.props("line.separator")),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationAkka,new ConfigurationProp,new ConfigurationEnv))

        val config = Config(
          cron = { if(! configArgs.cron.isEmpty) configArgs.cron else confuration.getString("crontab.cron").getOrElse("*/1 * * * * *") },
          quartz = { if(! configArgs.quartz.isEmpty) configArgs.quartz else "default" },
          
        )

        Console.err.println(s"Config: ${config}")

        new Cron((elapsed:Long) => {
            //val flow = pipe.run(NppData())
            println(s"${System.currentTimeMillis}: ${Thread.currentThread}: Ping: ${elapsed}")
            true
          },
          config.cron,
          conf = if(config.quartz == "default") None else Some((config.quartz,confuration))
        ).start
        
        
      }
      case _ => 
    }
  }
}

