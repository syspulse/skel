package io.syspulse.skel.cron

import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv,ConfigurationProp}

import scopt.OParser
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(
  cron:String = "",
)

object CronApp  {

  def main(args:Array[String]):Unit = {
    Console.err.println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]("cron").action((x, c) => c.copy(cron = x)).text("cron expression (def: '*/1 * * * * *'"),

      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationAkka,new ConfigurationProp,new ConfigurationEnv))

        val config = Config(
          cron = { if(! configArgs.cron.isEmpty) configArgs.cron else confuration.getString("cron").getOrElse("*/1 * * * * *") },

          
        )

        Console.err.println(s"Config: ${config}")

        new Cron((elapsed:Long) => {
            //val flow = pipe.run(NppData())
            println(s"${System.currentTimeMillis}: Ping: ${elapsed}")
            true
          },
          config.cron
        ).start
        
        
      }
      case _ => 
    }
  }
}

