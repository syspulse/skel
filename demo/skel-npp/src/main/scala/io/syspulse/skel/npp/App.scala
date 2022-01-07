package io.syspulse.skel.npp

import io.prometheus.client.Counter

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.cron.Cron
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
  nppCron:String = "",
  nppInterval:Long = -1L,
  nppDelay:Long = -1L,
  nppVariance:Long = -1L,
  
  format:String = "",

  influxUri:String = "",
  influxOrg:String = "",
  influxBucket:String = "",
  influxToken:String = "",
)

object App extends skel.Server {

  val metricCount: Counter = Counter.build().name("npp_total").help("NPP Telemetry total requests").register()
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('h', "http.host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "http.port").action((x, c) => c.copy(port = x)).text("port"),
        opt[String]('u', "http.uri").action((x, c) => c.copy(uri = x)).text("uri"),

        opt[String]('n', "npp.url").action((x, c) => c.copy(nppUrl = x)).text("npp url"),
        opt[String]('f', "npp.format").action((x, c) => c.copy(format = x)).text("format (csv/json or none)"),
        opt[String]("npp.cron").action((x, c) => c.copy(nppCron = x)).text("cron expression (def: '0 30 14 * * *'"),
        opt[Long]("npp.interval").action((x, c) => c.copy(nppInterval = x)).text("repeat interval in msec (omit if none)"),
        opt[Long]("npp.delay").action((x, c) => c.copy(nppDelay = x)).text("delay between requests (msec)"),
        opt[Long]("npp.variance").action((x, c) => c.copy(nppVariance = x)).text("varinace in delay (msec)"),

        opt[String]("influx.uri").action((x, c) => c.copy(influxUri = x)).text("Influx Uri (http://localhost:8086)"),
        opt[String]("influx.org").action((x, c) => c.copy(influxOrg = x)).text("Influx Org"),
        opt[String]("influx.bucket").action((x, c) => c.copy(influxBucket = x)).text("Influx Bucket"),
        opt[String]("influx.token").action((x, c) => c.copy(influxToken = x)).text("Influx Token"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.default

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else configuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else configuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else configuration.getString("http.uri").getOrElse("/api/v1/npp") },
          
          nppUrl = { if(! configArgs.nppUrl.isEmpty) configArgs.nppUrl else configuration.getString("npp.url").getOrElse("http://localhost:30004/MEDO-PS") },
          nppCron = { if(! configArgs.nppCron.isEmpty) configArgs.nppCron else configuration.getString("npp.cron").getOrElse("0 30 14 * * *") },
          nppInterval = { if(configArgs.nppInterval != -1L) configArgs.nppInterval else configuration.getLong("npp.interval").getOrElse(10000L) },
          nppDelay = { if(configArgs.nppDelay != -1L) configArgs.nppDelay else configuration.getLong("npp.delay").getOrElse(100L) },
          nppVariance = { if(configArgs.nppInterval != -1L) configArgs.nppInterval else configuration.getLong("npp.variance").getOrElse(100L) },
          
          format = { if(! configArgs.format.isEmpty) configArgs.format else configuration.getString("npp.format").getOrElse("csv") },

          influxUri = { if(! configArgs.influxUri.isEmpty) configArgs.influxUri else configuration.getString("influx.uri").getOrElse("http://localhost:8086") },
          influxOrg = { if(! configArgs.influxOrg.isEmpty) configArgs.influxOrg else configuration.getString("influx.org").getOrElse("") },
          influxBucket = { if(! configArgs.influxBucket.isEmpty) configArgs.influxBucket else configuration.getString("influx.bucket").getOrElse("") },
          influxToken = { if(! configArgs.influxToken.isEmpty) configArgs.influxToken else configuration.getString("influx.token").getOrElse("") },
        )

        Console.err.println(s"Config: ${config}")

        val pipe = new Pipeline[NppData]("NPP-Pipeline",
          stages = List(
            new NppScrap(rootUrl = config.nppUrl,delay = config.nppDelay, delayVariance = config.nppVariance),
            new NppDecode(),
            new NppPrint(format = config.format),
            new NppInflux(config.influxUri,config.influxOrg,config.influxBucket,config.influxToken)
          )
        )

        new Cron((elapsed:Long) => {
            val flow = pipe.run(NppData())
            metricCount.inc()
            true // always repeat
          },
          config.nppCron
        ).start
        
        run( config.host, config.port,config.uri,configuration,
          Seq()
        )
      }
      case _ => 
    }
  }
}

