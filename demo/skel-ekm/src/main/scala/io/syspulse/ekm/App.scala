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

import scala.concurrent.{Await, ExecutionContext, Future}

case class Config(
  
  ekmUri:String = "",    //"ekm.uri","http://io.ekmpush.com"),
  ekmKey:String = "",     //"ekm.key",""),
  ekmDevice:String = "",  //"ekm.device",""),
  ekmFreq:Long = 0L,      //,"ekm.freq",60L),
  
  limit:Long = 0L,        //"ticks",0L),

  logFile:String = "",    //"log.file",""),
  
  grafiteUri:String = "", //"grafite.uri","localhost:2003"),

  influxUri:String = "",  //"influx.uri","http://localhost:8086"),
  influxUser:String = "", //"influx.user","ekm_user"),
  influxPass:String = "", //"influx.pass","ekm_pass"),
  influxDb:String = "",   //"influx.db","ekm_db"),

  sinks:Seq[String] = Seq()
)

trait SkelApp {
  val log = Logger(s"${this}")
  def initPrometheus(configuration:Configuration) = {

    // init Prometheus
    val (pHost,pPort) = Util.getHostPort(configuration.getString("prometheus.listen").getOrElse("0.0.0.0:9091"))
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
    Console.err.println(s"args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]("grafite.uri").action((x, c) => c.copy(influxUri = x)).text("Grafite Uri (localhost:2003)"),
        
        opt[String]("influx.uri").action((x, c) => c.copy(influxUri = x)).text("Influx Uri (http://localhost:8086)"),
        opt[String]("influx.user").action((x, c) => c.copy(influxUser = x)).text("Influx(v1) user"),
        opt[String]("influx.pass").action((x, c) => c.copy(influxPass = x)).text("Influx(v1) pass"),
        opt[String]("influx.db").action((x, c) => c.copy(influxDb = x)).text("Influx(v1) DB"),

        opt[String]("ekm.uri").action((x, c) => c.copy(ekmUri = x)).text("EKM Uri (http://io.ekmpush.com)"),
        opt[String]("ekm.key").action((x, c) => c.copy(ekmKey = x)).text("EKM Key (base64 key)"),
        opt[String]("ekm.device").action((x, c) => c.copy(ekmDevice = x)).text("EKM Device (00000)"),
        opt[Long]("ekm.freq").action((x, c) => c.copy(ekmFreq = x.toLong)).text("EKM Poll Frequence (def=60 second)"),

        opt[Long]("limit").action((x, c) => c.copy(limit = x.toLong)).text("Ticks Limit (def=0)"),
        
        opt[String]("log.file").action((x, c) => c.copy(logFile = x)).text("Logfile (/tmp/file.log)"),
        
        arg[String]("<sinks>...").unbounded().optional()
          .action((x, c) => c.copy(sinks = c.sinks :+ x)).text("Sinks"), note("" + sys.props("line.separator")),

      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.default

        val config = configArgs.copy(
          
          influxUri = { if(! configArgs.influxUri.isEmpty) configArgs.influxUri else configuration.getString("influx.uri").getOrElse("http://localhost:8086") },
          influxUser = { if(! configArgs.influxUser.isEmpty) configArgs.influxUser else configuration.getString("influx.user").getOrElse("") },
          influxPass = { if(! configArgs.influxPass.isEmpty) configArgs.influxPass else configuration.getString("influx.pass").getOrElse("") },
          influxDb = { if(! configArgs.influxDb.isEmpty) configArgs.influxDb else configuration.getString("influx.db").getOrElse("") },

          ekmUri = { if(! configArgs.ekmUri.isEmpty) configArgs.ekmUri else configuration.getString("ekm.uri").getOrElse("http://localhost:30001") },
          ekmKey = { if(! configArgs.ekmKey.isEmpty) configArgs.ekmKey else configuration.getString("ekm.key").getOrElse("") },
          ekmDevice = { if(! configArgs.ekmDevice.isEmpty) configArgs.ekmDevice else configuration.getString("ekm.device").getOrElse("") },
          ekmFreq = { if(configArgs.ekmFreq != 0L) configArgs.ekmFreq else configuration.getLong("ekm.freq").getOrElse(60L) },

          limit = { if(configArgs.limit != 0L) configArgs.limit else configuration.getLong("limit").getOrElse(0L) },
          
          logFile = { if(! configArgs.logFile.isEmpty) configArgs.logFile else configuration.getString("log.file").getOrElse("") },
          
          grafiteUri = { if(! configArgs.grafiteUri.isEmpty) configArgs.grafiteUri else configuration.getString("grafite.uri").getOrElse("localhost:2003") },
        )

        initPrometheus(configuration)

        Console.err.println(s"Config: ${config}")

        val output = config.sinks.headOption.getOrElse("stdout").toLowerCase match {
          case "influx" => new SunkInflux(config)
          case "grafite" => new SunkGrafite(config)
          case _ => new SunkPrint(config)
        }

        val stream = output.run()

        Console.err.println(s"Stream: ${stream}")
        
        val r = Await.result(stream, Duration.Inf)
        //println(result.value.asInstanceOf[scala.util.Failure[_]].exception.getStackTrace.mkString("\n"))
        Console.err.println(s"Stream: ${stream}: ${r}")
      }
      case _ => System.exit(1)
    }
  }
}