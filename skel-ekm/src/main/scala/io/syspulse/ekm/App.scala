package io.syspulse.ekm

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

// case class Config(
//   dataSource:String = "",
  
//   ekmKey:String = "",
//   ekmDevice:String = "",
//   ekmInterval:Long = 1L,
  
//   ticks:Long = 0L,

//   logFile:String = "",
//   grafiteUri:String = "",
//   influxUri:String = "",
//   influxUser:String = "",
//   influxPass:String = "",
//   influxDb:String = "",
// )

case class Config(
  dataSource:(String,String,String) = ("","ekm.data-source","influx"),
  
  ekmHost:(String,String,String) = ("","ekm.ekm-host","http://io.ekmpush.com"),
  ekmKey:(String,String,String) = ("","ekm.ekm-key",""),
  ekmDevice:(String,String,String) = ("","ekm.ekm-device",""),
  ekmFreq:(Long,String,Long) = (0L,"ekm.ekm-freq",60L),
  
  ticks:(Long,String,Long) = (0L,"ticks",0L),

  logFile:(String,String,String) = ("","ekm.log-file",""),
  grafiteUri:(String,String,String) = ("","ekm.grafite-uri","localhost:2003"),
  influxUri:(String,String,String) = ("","ekm.influx-uri","http://localhost:8086"),
  influxUser:(String,String,String) = ("","ekm.influx-user","ekm_user"),
  influxPass:(String,String,String) = ("","ekm.influx-pass","ekm_pass"),
  influxDb:(String,String,String) = ("","ekm.influx-db","ekm_db"),
)

trait SkelApp {
  val log = Logger(s"${this}")
  def initSkel(configuration:Configuration) = {

    // init Prometheus
    val (pHost,pPort) = Util.getHostPort(configuration.getString("ekm.prometheus.listen").getOrElse("0.0.0.0:9091"))
    val prometheusListener = new HTTPServer(pHost,pPort)
    DefaultExports.initialize()
    //new GarbageCollectorExports().register()
    //new MemoryPoolsExports().register()
    //new ThreadExports().register()
    log.info(s"prometheus: ${pHost}:${pPort}: ${prometheusListener}")
  }
}

object App extends SkelApp {
  
  def main(args:Array[String]): Unit = {
    println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('g', "grafite-uri").action((x, c) => c.copy(grafiteUri = (x,c.grafiteUri._2,c.grafiteUri._3))).text("Grafite Uri (localhost:2003)"),
        
        opt[String]('i', "influx-uri").action((x, c) => c.copy(influxUri = (x,c.influxUri._2,c.influxUri._3))).text("Influx Uri (http://localhost:8086)"),
        opt[String]('u', "influx-user").action((x, c) => c.copy(influxUser = (x,c.influxUser._2,c.influxUser._3))).text("Influx user"),
        opt[String]('p', "influx-pass").action((x, c) => c.copy(influxPass = (x,c.influxPass._2,c.influxPass._3))).text("Influx pass"),
        opt[String]('d', "influx-db").action((x, c) => c.copy(influxDb = (x,c.influxDb._2,c.influxDb._3))).text("Influx db"),

        opt[String]('e', "ekm-host").action((x, c) => c.copy(ekmHost = (x,c.ekmHost._2,c.ekmHost._3))).text("EKM Host (http://io.ekmpush.com)"),
        opt[String]('k', "ekm-key").action((x, c) => c.copy(ekmKey = (x,c.ekmKey._2,c.ekmKey._3))).text("EKM Key ()"),
        opt[String]('m', "ekm-device").action((x, c) => c.copy(ekmDevice = (x,c.ekmDevice._2,c.ekmDevice._3))).text("EKM Device (00000)"),
        opt[Long]('v', "ekm-freq").action((x, c) => c.copy(ekmFreq = (x.toLong,c.ekmFreq._2,c.ekmFreq._3))).text("EKM Poll Frequence (def=60 second)"),
        
        opt[Long]('t', "ticks").action((x, c) => c.copy(ticks = (x.toLong,c.ticks._2,c.ticks._3))).text("Ticks Limit (def=0)"),
        
        opt[String]('l', "log-file").action((x, c) => c.copy(logFile = (x,c.logFile._2,c.logFile._3))).text("Logfile (/tmp/file.log)"),
        
        cmd("influx").action( (_, c) => c.copy(dataSource = ("influx",c.dataSource._2,c.dataSource._3)) ).text("Influx DataSource.").children(),
        cmd("grafite").action( (_, c) => c.copy(dataSource = ("grafite",c.dataSource._2,c.dataSource._3)) ).text("Grafite DataSource.").children()
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          dataSource = ({ if(! configArgs.dataSource._1.isEmpty) configArgs.dataSource._1 else confuration.getString(configArgs.dataSource._2).getOrElse(configArgs.dataSource._3) },configArgs.dataSource._2,configArgs.dataSource._3),
          
          ekmHost = ({ if(! configArgs.ekmHost._1.isEmpty) configArgs.ekmHost._1 else confuration.getString(configArgs.ekmHost._2).getOrElse(configArgs.ekmHost._3) },configArgs.ekmHost._2,configArgs.ekmHost._3),
          ekmKey = ({ if(! configArgs.ekmKey._1.isEmpty) configArgs.ekmKey._1 else confuration.getString(configArgs.ekmKey._2).getOrElse(configArgs.ekmKey._3) },configArgs.ekmKey._2,configArgs.ekmKey._3),
          ekmDevice = ({ if(! configArgs.ekmDevice._1.isEmpty) configArgs.ekmDevice._1 else confuration.getString(configArgs.ekmDevice._2).getOrElse(configArgs.ekmDevice._3) },configArgs.ekmDevice._2,configArgs.ekmDevice._3),
          ekmFreq = ({ if(configArgs.ekmFreq._1 != 0) configArgs.ekmFreq._1 else confuration.getLong(configArgs.ekmFreq._2).getOrElse(configArgs.ekmFreq._3) },configArgs.ekmFreq._2,configArgs.ekmFreq._3),
          
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

        config.dataSource._1 match {
          case "influx" => (new EkmTelemetryInfluxdb).run(config.ekmHost._1,config.ekmKey._1,config.ekmDevice._1,config.ekmFreq._1, config.ticks._1, config.logFile._1, config.influxUri._1, config.influxUser._1, config.influxPass._1, config.influxDb._1)
          case "grafite" => (new EkmTelemetryGrafite).run(config.ekmHost._1,config.ekmKey._1,config.ekmDevice._1,config.ekmFreq._1, config.ticks._1, config.logFile._1, config.grafiteUri._1)
        }
      }
      case _ => 
    }
  }
}