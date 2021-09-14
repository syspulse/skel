package io.syspulse.skel.scrap.npp

import io.prometheus.client.Counter

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.util.Cron
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv,ConfigurationProp}

import io.syspulse.skel.flow._

import scopt.OParser
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  nppUrl:String = "",
  format:String = "",
  interval:Long = -1L
)

object App extends skel.Server {

  val metricCount: Counter = Counter.build().name("skel_npp_total").help("NPP Telemetry total requests").register()
  
  def main(args:Array[String]) = {
    Console.err.println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("port"),
        opt[String]('u', "uri").action((x, c) => c.copy(uri = x)).text("uri"),
        opt[String]('d', "npp").action((x, c) => c.copy(nppUrl = x)).text("npp url"),
        opt[String]('f', "format").action((x, c) => c.copy(format = x)).text("format (csv/json or none)"),
        opt[Long]('i', "interval").action((x, c) => c.copy(interval = x)).text("repeat interval in msec (omit if none)"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationProp,new ConfigurationAkka))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else confuration.getString("http.uri").getOrElse("/api/v1/npp") },
          nppUrl = { if(! configArgs.nppUrl.isEmpty) configArgs.nppUrl else confuration.getString("npp.url").getOrElse("http://localhost:30004/MEDO-PS") },
          format = { if(! configArgs.format.isEmpty) configArgs.format else confuration.getString("format").getOrElse("csv") },
          interval = { if(configArgs.interval != -1L) configArgs.interval else confuration.getLong("npp.interval").getOrElse(10000L) },
        )

        Console.err.println(s"Config: ${config}")

        val pipe = new Pipeline[NppData]("NPP-Pipeline",
          stages = List(
            new NppScrap(rootUrl = config.nppUrl,delay=0L),
            new NppDecode(),
            new NppPrint(format = config.format),
            new NppInflux(confuration.getString("influx.uri").getOrElse("http://localhost:8086"),
                          confuration.getString("influx.org").getOrElse(""),
                          confuration.getString("influx.token").getOrElse(""),
                          confuration.getString("influx.bucket").getOrElse("npp-bucket"))
          )
        )

        new Cron(FiniteDuration(config.interval,TimeUnit.MILLISECONDS),() => {
            val flow = pipe.run(NppData())
            metricCount.inc()
          },
          delay = 0L
        )
        
        
        run( config.host, config.port,config.uri,confuration,
          Seq()
        )
      }
      case _ => 
    }
  }
}

