package io.syspulse.skel.pdf

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel.pdf.issue._
import io.syspulse.skel.pdf.report.store._
import io.syspulse.skel.pdf.report.server.ReportRoutes

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",
  datastore:String = "",

  templateDir:String = "",
  issuesDir:String = "",
  outputFile:String = "",

  cmd:String = "",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]):Unit = {
    println(s"args: '${args.mkString(",")}'")

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"pdf-report","",
        ArgString('h', "http.host","listen host (def: 0.0.0.0)"),
        ArgInt('p', "http.port","listern port (def: 8080)"),
        ArgString('u', "http.uri","api uri (def: /api/v1/report)"),
        ArgString('d', "datastore","datastore [mysql,postgres,mem,cache] (def: mem)"),

        ArgString('_', "report.template-dir","datastore [mysql,postgres,mem,cache] (def: mem)"),
        ArgString('_', "report.issues-dir","datastore [mysql,postgres,mem,cache] (def: mem)"),
        ArgString('_', "report.output-file","datastore [mysql,postgres,mem,cache] (def: mem)"),

        ArgCmd("server","Command"),
        ArgCmd("generate","Command"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      host = c.getString("http.host").getOrElse("0.0.0.0"),
      port = c.getInt("http.port").getOrElse(8080),
      uri = c.getString("http.uri").getOrElse("/api/v1/report"),
      datastore = c.getString("datastore").getOrElse("mem"),

      templateDir = c.getString("report.template-dir").getOrElse("templates/T0"),
      issuesDir = c.getString("report.issues-dir").getOrElse("./projects/Project-1/issues/"),
      outputFile = c.getString("report.output-file").getOrElse("output.pdf"),

      cmd = c.getCmd().getOrElse("server"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")

    val store = config.datastore match {
      case "mem" | "cache" => new ReportStoreMem
      case _ => {
        Console.err.println(s"Uknown datastore: '${config.datastore}': using 'mem'")
        new ReportStoreMem
      }
    }

    config.cmd match {
      case "server" => 
        run( config.host, config.port,config.uri, c,
          Seq(
            (ReportRegistry(store),"ReportRegistry",(r, ac) => new ReportRoutes(r)(ac,config) )
          )
        )
      case "generate" => {
        val r = new ReportGenerator(config.templateDir,config.issuesDir,config.outputFile).generate()
        println(s"report: ${r}")
        System.exit(0)
      }
    }
  }
}

