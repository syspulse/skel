package io.syspulse.skel.cron

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.annotation.meta.param

case class Config(
  // expr:String = "*/1 * * * * ?", //"0/20 * * * * ?"
  quartz:String = "default",

  cmd:String = "cron",
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
      new ConfigurationArgs(args,"skel-cron","",
        // ArgString('c', "cron.expr",s"cron expression (use '_': '--cron.expr='*/1_*_*_*_*_?') (def: ${d.expr})"),
        ArgString('q', "cron.quartz",s"quartz config properties (def: default) (def: ${d.quartz})"),

        ArgCmd("cron","Cron command"),
        ArgCmd("freq","Frequency command (use cron.expr=10000)"),
        ArgParam("<params>",""),
        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    val config = Config(
      // expr = c.getString("cron.expr").getOrElse(d.expr),
      quartz = c.getString("cron.quartz").getOrElse(d.quartz),
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val r = config.cmd match {
      case "cron" =>         
        Cron((elapsed:Long) => {
            println(s"${System.currentTimeMillis}: ${Thread.currentThread}: Ping: ${elapsed}")
            true
          },
          config.params.mkString(" "),//config.expr,
          settings = Map()
        ).start()

      case "quartz" =>         
        new CronQuartz((elapsed:Long) => {
            println(s"${System.currentTimeMillis}: ${Thread.currentThread}: Ping: ${elapsed}")
            true
          },
          config.params.mkString(" "), //config.expr.replaceAll("_"," "),
          conf = if(config.quartz == "default") None else Some((config.quartz,c))
        ).start()

      case "freq" =>         
        new CronFreq((_) => {
            println(s"${System.currentTimeMillis}: ${Thread.currentThread}: Ping")
            true
          },
          config.params.mkString(" "),//config.expr, 
          
        ).start()
                      
      case _ => 
    }
    Console.err.println(s"r = ${r}")
  }
}

