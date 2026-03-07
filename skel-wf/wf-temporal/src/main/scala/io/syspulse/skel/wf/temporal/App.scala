package io.syspulse.skel.wf.temporal

import scala.util.Success

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.wf.temporal.por._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/wf/temporal",

  datastore:String = "temporal://",

  cmd:String = "wf",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {

  def main(args:Array[String]):Unit = {
    log.info(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv,
      new ConfigurationArgs(args,"wf-temporal","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [temporal://] (def: ${d.datastore})"),

        ArgCmd("wf",s"Server"),
        ArgCmd("temporal",s"Temporal subcommands"),
        ArgCmd("por-worker",s"Start PoR Temporal Worker"),
        ArgCmd("por-start",s"Start PoR Workflow - Usage: por-start <flow> [cex-name]"),

        ArgCmd("wf",s"Workflow subcommands: " +
          s"assemble name 'dsl'  : create Workflow with dsl commands, ex: 'F-1(LogExec(sys=1,log.level=WARN))->F-2(LogExec(sys=2))->F-3(TerminateExec())'" +
          s"load <id>            : Load workflow by id from store" +
          s"show <id>            : Show all workflows in store" +
          ""
        ),

        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),

      datastore = c.getString("datastore").getOrElse(d.datastore),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    log.info(s"Config: ${config}")


    val r = config.cmd match {
      case "wf" =>
        // Server mode (future implementation)
        "Server mode not implemented yet"

      case "temporal" =>
        // Generic temporal commands (future implementation)
        "Temporal commands not implemented yet"

      case "por-worker" =>
        // Start PoR Worker
        PorWorker.main(config.params.toArray)
        "PoR Worker started"

      case "por-start" =>
        // Start PoR Workflow
        PorStarter.main(config.params.toArray)
        "PoR Workflow executed"

      case _ =>
        s"Unknown command: ${config.cmd}"
    }

    log.info(s"${r}")
  }
}



