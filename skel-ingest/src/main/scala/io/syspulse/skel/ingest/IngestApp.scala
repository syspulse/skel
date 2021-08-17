package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import scopt.OParser

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}

import io.prometheus.client._
import io.prometheus.client.exporter._
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.hotspot.GarbageCollectorExports
import io.prometheus.client.hotspot.MemoryPoolsExports
import io.prometheus.client.hotspot.ThreadExports


case class Config(
  dataSink:(String,String,String) = ("","ingest.data-sink","stdout"),
  
  sourceHost:(String,String,String) = ("","ingest.source-host","http://localhost:3003"),
  sourceKey:(String,String,String) = ("","ingest.source-key",""),
  sourceDevice:(String,String,String) = ("","ingest.source-device","dev-0001"),
  sourceFreq:(Long,String,Long) = (0L,"ingest.source-freq",1000L),
  
  ticks:(Long,String,Long) = (0L,"ticks",0L),

  logFile:(String,String,String) = ("","ingest.log-file",""),
  grafiteUri:(String,String,String) = ("","ingest.grafite-uri","localhost:2003"),
  influxUri:(String,String,String) = ("","ingest.influx-uri","http://localhost:8086"),
  influxUser:(String,String,String) = ("","ingest.influx-user","ekm_user"),
  influxPass:(String,String,String) = ("","ingest.influx-pass","ekm_pass"),
  influxDb:(String,String,String) = ("","ingest.influx-db","ekm_db"),
)

trait Telemetring {
  val log = Logger(s"${this}")

  def initPrometheus(configuration:Configuration) = {

    // init Prometheus
    val (pHost,pPort) = Util.getHostPort(configuration.getString("prometheus.listen").getOrElse("0.0.0.0:9091"))
    val prometheusListener = new HTTPServer(pHost,pPort)
    DefaultExports.initialize()
    //new GarbageCollectorExports().register()
    //new MemoryPoolsExports().register()
    //new ThreadExports().register()
    log.info(s"Prometheus: listen(${pHost}:${pPort}): ${prometheusListener}")
  }
}

object IngestApp extends Telemetring {
  
  def main(args:Array[String]): Unit = {
    println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName("skel-ingest"), head("skel-ingest", "0.0.1"),
        opt[String]('g', "grafite-uri").action((x, c) => c.copy(grafiteUri = (x,c.grafiteUri._2,c.grafiteUri._3))).text("Grafite Uri (localhost:2003)"),
        
        opt[String]('i', "influx-uri").action((x, c) => c.copy(influxUri = (x,c.influxUri._2,c.influxUri._3))).text("Influx Uri (http://localhost:8086)"),
        opt[String]('u', "influx-user").action((x, c) => c.copy(influxUser = (x,c.influxUser._2,c.influxUser._3))).text("Influx user"),
        opt[String]('p', "influx-pass").action((x, c) => c.copy(influxPass = (x,c.influxPass._2,c.influxPass._3))).text("Influx pass"),
        opt[String]('d', "influx-db").action((x, c) => c.copy(influxDb = (x,c.influxDb._2,c.influxDb._3))).text("Influx db"),

        opt[String]('e', "source-host").action((x, c) => c.copy(sourceHost = (x,c.sourceHost._2,c.sourceHost._3))).text("Source Host (http://localhost:3003)"),
        opt[String]('k', "source-key").action((x, c) => c.copy(sourceKey = (x,c.sourceKey._2,c.sourceKey._3))).text("Source Key ()"),
        opt[String]('m', "source-device").action((x, c) => c.copy(sourceDevice = (x,c.sourceDevice._2,c.sourceDevice._3))).text("Source Device (00000)"),
        opt[Long]('v', "source-freq").action((x, c) => c.copy(sourceFreq = (x.toLong,c.sourceFreq._2,c.sourceFreq._3))).text("Source Poll Frequence (def=60 second)"),
        
        opt[Long]('t', "ticks").action((x, c) => c.copy(ticks = (x.toLong,c.ticks._2,c.ticks._3))).text("Ticks Limit (def=0)"),
        
        opt[String]('l', "log-file").action((x, c) => c.copy(logFile = (x,c.logFile._2,c.logFile._3))).text("Logfile (/tmp/file.log)"),
        
        cmd("influx").action( (_, c) => c.copy(dataSink = ("influx",c.dataSink._2,c.dataSink._3)) ).text("Influx DataSink.").children(),
        cmd("grafite").action( (_, c) => c.copy(dataSink = ("grafite",c.dataSink._2,c.dataSink._3)) ).text("Grafite DataSink.").children(),
        cmd("stdout").action( (_, c) => c.copy(dataSink = ("stdout",c.dataSink._2,c.dataSink._3)) ).text("Stdout DataSink.").children(),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          dataSink = ({ if(! configArgs.dataSink._1.isEmpty) configArgs.dataSink._1 else confuration.getString(configArgs.dataSink._2).getOrElse(configArgs.dataSink._3) },configArgs.dataSink._2,configArgs.dataSink._3),
          
          sourceHost = ({ if(! configArgs.sourceHost._1.isEmpty) configArgs.sourceHost._1 else confuration.getString(configArgs.sourceHost._2).getOrElse(configArgs.sourceHost._3) },configArgs.sourceHost._2,configArgs.sourceHost._3),
          sourceKey = ({ if(! configArgs.sourceKey._1.isEmpty) configArgs.sourceKey._1 else confuration.getString(configArgs.sourceKey._2).getOrElse(configArgs.sourceKey._3) },configArgs.sourceKey._2,configArgs.sourceKey._3),
          sourceDevice = ({ if(! configArgs.sourceDevice._1.isEmpty) configArgs.sourceDevice._1 else confuration.getString(configArgs.sourceDevice._2).getOrElse(configArgs.sourceDevice._3) },configArgs.sourceDevice._2,configArgs.sourceDevice._3),
          sourceFreq = ({ if(configArgs.sourceFreq._1 != 0) configArgs.sourceFreq._1 else confuration.getLong(configArgs.sourceFreq._2).getOrElse(configArgs.sourceFreq._3) },configArgs.sourceFreq._2,configArgs.sourceFreq._3),
          
          ticks = ({ if(configArgs.ticks._1 != 0) configArgs.ticks._1 else confuration.getLong(configArgs.ticks._2).getOrElse(configArgs.ticks._3) },configArgs.ticks._2,configArgs.ticks._3),
          
          logFile = ({ if(! configArgs.logFile._1.isEmpty) configArgs.logFile._1 else confuration.getString(configArgs.logFile._2).getOrElse(configArgs.logFile._3) },configArgs.logFile._2,configArgs.logFile._3),
          grafiteUri = ({ if(! configArgs.grafiteUri._1.isEmpty) configArgs.grafiteUri._1 else confuration.getString(configArgs.grafiteUri._2).getOrElse(configArgs.grafiteUri._3) },configArgs.grafiteUri._2,configArgs.grafiteUri._3),
          influxUri = ({ if(! configArgs.influxUri._1.isEmpty) configArgs.influxUri._1 else confuration.getString(configArgs.influxUri._2).getOrElse(configArgs.influxUri._3) },configArgs.influxUri._2,configArgs.influxUri._3),
          influxUser = ({ if(! configArgs.influxUser._1.isEmpty) configArgs.influxUser._1 else confuration.getString(configArgs.influxUser._2).getOrElse(configArgs.influxUser._3) },configArgs.influxUser._2,configArgs.influxUser._3),
          influxPass = ({ if(! configArgs.influxPass._1.isEmpty) configArgs.influxPass._1 else confuration.getString(configArgs.influxPass._2).getOrElse(configArgs.influxPass._3) },configArgs.influxPass._2,configArgs.influxPass._3),
          influxDb = ({ if(! configArgs.influxDb._1.isEmpty) configArgs.influxDb._1 else confuration.getString(configArgs.influxDb._2).getOrElse(configArgs.influxDb._3) },configArgs.influxDb._2,configArgs.influxDb._3),
        )

        initSkel(confuration)

        println(s"Config: ${config}")

        config.dataSink._1 match {
          //case "influx" => (new EkmTelemetryInfluxdb).run(config.sourceHost._1,config.sourceKey._1,config.sourceDevice._1,config.sourceFreq._1, config.ticks._1, config.logFile._1, config.influxUri._1, config.influxUser._1, config.influxPass._1, config.influxDb._1)
          //case "grafite" => (new EkmTelemetryGrafite).run(config.sourceHost._1,config.sourceKey._1,config.sourceDevice._1,config.sourceFreq._1, config.ticks._1, config.logFile._1, config.grafiteUri._1)
          case "stdout" | "" => (new IngestStdout).run(config.sourceHost._1,config.sourceKey._1,config.sourceDevice._1,config.sourceFreq._1, config.ticks._1, config.logFile._1)
        }
      }
      case _ => 
    }
  }
}