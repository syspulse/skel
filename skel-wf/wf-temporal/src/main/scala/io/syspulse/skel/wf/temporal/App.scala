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

  //datastore:String = "",
  engine:String = "temporal://",
  wf:String = "demo://",

  porOwnerName: String = "DefaultOwner",
  porFlow: String = "flow-1",
  porPolSignalMode: String = "simulate",
  porTags: Seq[String] = Seq(),
  porMemo: Map[String,String] = Map("region" -> "US", "env" -> "test"),

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

        // ArgString('d', "datastore",s"Datastore [temporal://] (def: ${d.datastore})"),
        ArgString('e', "engine",s"Engine URI [temporal://] (def: ${d.engine})"),
        ArgString('w', "wf",s"Workflow implementation [demo://] (def: ${d.wf})"),

        ArgString('_', "por.owner.name",s"PoR owner name (def: ${d.porOwnerName})"),
        ArgString('_', "por.flow",s"PoR flow: flow-1|flow-2|flow-3|flow-4|flow-5 (def: ${d.porFlow})"),
        ArgString('_', "por.pol.signal-mode",s"PoL signal mode: file|rest|simulate (def: ${d.porPolSignalMode})"),
        ArgString('_', "por.tags",s"PoR workflow tags (comma-separated, e.g., CEX,Bybit) (def: ${d.porTags.mkString(",")})"),
        ArgString('_', "por.memo",s"PoR workflow memo (key=value pairs, comma-separated, e.g., region=US,env=prod) (def: ${d.porMemo.mkString(",")})"),

        ArgCmd("wf",s"Server"),
        ArgCmd("temporal",s"Temporal subcommands"),
        ArgCmd("por-worker",s"Start PoR Temporal Worker"),
        ArgCmd("por-start",s"Start PoR Workflow"),

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

      // datastore = c.getString("datastore").getOrElse(d.datastore),
      engine = c.getString("engine").getOrElse(d.engine),
      wf = c.getString("wf").getOrElse(d.wf),

      porOwnerName = c.getString("por.owner.name").getOrElse(d.porOwnerName),
      porFlow = c.getString("por.flow").getOrElse(d.porFlow),
      porPolSignalMode = c.getString("por.pol.signal-mode").getOrElse(d.porPolSignalMode),
      porTags = c.getListString("por.tags",d.porTags),
      porMemo = c.getMap("por.memo",d.porMemo),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    log.info(s"Config: ${config}")

    val impl = config.wf.split("://").toList match {
      case "demo" :: Nil => new skel.wf.temporal.por.demo.PorActivitiesDemo()
      case _ => {
        Console.err.println(s"Unknown workflow implementation: '${config.wf}")
        sys.exit(1)
      }
    }
    
    log.info(s"Workflow: ${impl}")

    val r = config.cmd match {
      case "wf" =>
        // Server mode (future implementation)
        "Server mode not implemented yet"

      case "temporal" =>
        config.params.toList match {
          case "query" :: query :: Nil =>
            Temporal.query(config.engine, query)

          case "query" :: query :: pageSize :: Nil =>
            Temporal.query(config.engine, query, pageSize.toInt)

          case "describe" :: workflowId :: Nil =>
            Temporal.describe(config.engine, workflowId)

          case "describe" :: workflowId :: runId :: Nil =>
            Temporal.describe(config.engine, workflowId, Some(runId))

          case "list" :: Nil =>
            Temporal.list(config.engine)

          case "list" :: ("--status" | "-s") :: status :: tail =>
            val pageSize = tail.headOption.map(_.toInt).getOrElse(10)
            Temporal.list(config.engine, status = Some(status), pageSize = pageSize)

          case "list" :: ("--type" | "-t") :: workflowType :: tail =>
            val pageSize = tail.headOption.map(_.toInt).getOrElse(10)
            Temporal.list(config.engine, workflowType = Some(workflowType), pageSize = pageSize)

          case _ =>
            """Usage: temporal <command> [options]

Commands:
  query <query> [pageSize]              - Query workflows using Temporal query syntax
                                          Example: query "WorkflowId = 'por-workflow-*'" 20

  describe <workflowId> [runId]         - Get detailed information about a workflow
                                          Example: describe por-workflow-Binance-123456

  list [--status|-s <status>]           - List workflows with optional filters
       [--type|-t <type>]                 Status: Running, Completed, Failed, Canceled, etc.
       [pageSize]                         Example: list --status Running 20

Examples:
  temporal query "ExecutionStatus = 'Running'"
  temporal query "WorkflowType = 'PorWorkflow' AND ExecutionStatus = 'Running'" 50
  temporal describe por-workflow-Binance-1234567890
  temporal list --status Running
  temporal list --type PorWorkflow 25
"""
        }

      case "por-worker" =>
        PorWorker.run(config.engine, impl)        

      case "por-start" =>
        // Parse flow to determine required steps if not explicitly set
        val (pooRequired, porRequired, polRequired, reportRequired) = config.porFlow.toLowerCase match {
          case "flow-1" => (true, true, true, true)   // PoO -> PoR -> PoL -> Solvency -> Report
          case "flow-2" => (false, true, true, true)  // PoR -> PoL -> Solvency -> Report
          case "flow-3" => (false, true, false, true) // PoR -> Report
          case "flow-4" => (true, true, false, true)  // PoO -> PoR -> Report
          case "flow-5" => (false, false, true, false)  // PoL
          case _ =>
            log.warn(s"Unknown flow: ${config.porFlow}, using default flow-1")
            (true, true, true, true)
        }

        // Parse memo from key=value pairs
        
        val porConfig = PorConfig(
          ownerName = config.porOwnerName,
          flow = config.porFlow,
          pooRequired = pooRequired,
          porRequired = porRequired,
          polRequired = polRequired,
          reportRequired = reportRequired,
          polSignalMode = config.porPolSignalMode,
          tags = config.porTags,
          memo = config.porMemo
        )

        PorStarter.run(config.engine, porConfig)        

      case _ =>
        s"Unknown command: ${config.cmd}"
    }

    Console.out.println(s"${r}")
  }
}



