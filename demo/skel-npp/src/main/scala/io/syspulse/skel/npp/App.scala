package io.syspulse.skel.npp

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
  interval:Long = -1L,
  influxUri:String = "",
  influxOrg:String = "",
  influxBucket:String = "",
  influxToken:String = "",
)

object App extends skel.Server {

  val metricCount: Counter = Counter.build().name("npp_total").help("NPP Telemetry total requests").register()
  
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
        opt[String]('n', "npp.url").action((x, c) => c.copy(nppUrl = x)).text("npp url"),
        opt[String]('f', "format").action((x, c) => c.copy(format = x)).text("format (csv/json or none)"),
        opt[Long]('i', "npp.interval").action((x, c) => c.copy(interval = x)).text("repeat interval in msec (omit if none)"),

        opt[String]("influx.uri").action((x, c) => c.copy(influxUri = x)).text("Influx Uri (http://localhost:8086)"),
        opt[String]("influx.org").action((x, c) => c.copy(influxOrg = x)).text("Influx Org"),
        opt[String]("influx.bucket").action((x, c) => c.copy(influxBucket = x)).text("Influx Bucket"),
        opt[String]("influx.token").action((x, c) => c.copy(influxToken = x)).text("Influx Token"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationAkka,new ConfigurationProp,new ConfigurationEnv))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else confuration.getString("http.uri").getOrElse("/api/v1/npp") },
          nppUrl = { if(! configArgs.nppUrl.isEmpty) configArgs.nppUrl else confuration.getString("npp.url").getOrElse("http://localhost:30004/MEDO-PS") },
          format = { if(! configArgs.format.isEmpty) configArgs.format else confuration.getString("format").getOrElse("csv") },
          interval = { if(configArgs.interval != -1L) configArgs.interval else confuration.getLong("npp.interval").getOrElse(10000L) },

          influxUri = { if(! configArgs.influxUri.isEmpty) configArgs.influxUri else confuration.getString("influx.uri").getOrElse("http://localhost:8086") },
          influxOrg = { if(! configArgs.influxOrg.isEmpty) configArgs.influxOrg else confuration.getString("influx.org").getOrElse("") },
          influxBucket = { if(! configArgs.influxBucket.isEmpty) configArgs.influxBucket else confuration.getString("influx.bucket").getOrElse("") },
          influxToken = { if(! configArgs.influxToken.isEmpty) configArgs.influxToken else confuration.getString("influx.token").getOrElse("") },
        )

        Console.err.println(s"Config: ${config}")

        val pipe = new Pipeline[NppData]("NPP-Pipeline",
          stages = List(
            new NppScrap(rootUrl = config.nppUrl,delay=0L),
            new NppDecode(),
            new NppPrint(format = config.format),
            new NppInflux(config.influxUri,config.influxOrg,config.influxBucket,config.influxToken)
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

