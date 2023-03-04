package io.syspulse.skel.cron

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(
  expr:String = "*/1 * * * * ?", //"0/20 * * * * ?"
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
        // ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        // ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        // ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        // ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        ArgString('c', "cron.expr",s"cron expression (use '_': '--cron.expr='*/1_*_*_*_*_?') (def: ${d.expr})"),
        ArgString('q', "cron.quartz",s"quartz config properties (def: default) (def: ${d.quartz})"),

        // ArgCmd("server","Command"),
        ArgCmd("cron","Command"),
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      // host = c.getString("http.host").getOrElse(d.host),
      // port = c.getInt("http.port").getOrElse(d.port),
      // uri = c.getString("http.uri").getOrElse(d.uri),
      // datastore = c.getString("datastore").getOrElse(d.datastore),
      expr = c.getString("cron.expr").getOrElse(d.expr),
      quartz = c.getString("cron.quartz").getOrElse(d.quartz),
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    val r = config.cmd match {
      case "cron" =>         
        new Cron((elapsed:Long) => {
            println(s"${System.currentTimeMillis}: ${Thread.currentThread}: Ping: ${elapsed}")
            true
          },
          config.expr.replaceAll("_"," "),
          conf = if(config.quartz == "default") None else Some((config.quartz,c))
        ).start
                      
      case _ => 
    }
    Console.err.println(s"r = ${r}")
  }
}

