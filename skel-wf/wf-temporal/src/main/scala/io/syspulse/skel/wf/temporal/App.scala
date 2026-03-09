package io.syspulse.skel.wf.temporal

import scala.util.{Success, Try}
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.wf.temporal.por._
import io.syspulse.skel.wf.temporal.por.demo.DemoUtil
import io.syspulse.skel.wf.temporal.workflow.store._
import io.syspulse.skel.wf.temporal.workflow.server._

// Examples:
//   temporal query "ExecutionStatus = 'Running'"
//   temporal query "WorkflowType = 'PorWorkflow' AND ExecutionStatus = 'Running'" 50
//   temporal describe por-workflow-Binance-1234567890
//   temporal list --status Running
//   temporal list --type PorWorkflow 25

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/wf",

  datastore:String = "mem://",
  engine:String = "temporal://",
  wf:String = "demo://",

  porOwnerName: String = "DefaultOwner",
  porFlow: String = "flow-1",
  porPolSignalMode: String = "simulate",
  porTags: Seq[String] = Seq(),
  porMemo: Map[String,String] = Map("region" -> "US", "env" -> "test"),

  cmd:String = "server",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {

  def formatWorkflowInfo(info: WorkflowExecutionInfo): String = {
    val result = new StringBuilder
    result.append(s"Workflow Details:\n\n")
    result.append(s"Workflow ID: ${info.workflowId}\n")
    result.append(s"Run ID: ${info.runId}\n")
    result.append(s"Type: ${info.workflowType}\n")
    result.append(s"Status: ${info.status}\n")

    info.startTime.foreach { ts =>
      result.append(s"Start Time: ${new java.util.Date(ts)}\n")
    }

    info.closeTime.foreach { ts =>
      result.append(s"Close Time: ${new java.util.Date(ts)}\n")
    }

    if (info.memo.nonEmpty) {
      result.append(s"\nMemo:\n")
      info.memo.foreach { case (key, values) =>
        result.append(s"  $key: ${values.mkString(", ")}\n")
      }
    }

    if (info.searchAttributes.nonEmpty) {
      result.append(s"\nSearch Attributes:\n")
      info.searchAttributes.foreach { case (key, values) =>
        result.append(s"  $key: ${values.mkString(", ")}\n")
      }
    }

    result.toString
  }

  def formatQueryResult(result: QueryResult): String = {
    if (result.executions.isEmpty) {
      return "No workflows found"
    }

    val output = new StringBuilder
    output.append(s"Found ${result.executions.size} workflow(s):\n\n")

    result.executions.zipWithIndex.foreach { case (info, idx) =>
      output.append(s"${idx + 1}. Workflow ID: ${info.workflowId}\n")
      output.append(s"   Run ID: ${info.runId}\n")
      output.append(s"   Type: ${info.workflowType}\n")
      output.append(s"   Status: ${info.status}\n")

      info.startTime.foreach { ts =>
        output.append(s"   Start Time: ${new java.util.Date(ts)}\n")
      }

      info.closeTime.foreach { ts =>
        output.append(s"   Close Time: ${new java.util.Date(ts)}\n")
      }

      if (info.memo.nonEmpty) {
        output.append(s"   Memo: ${info.memo.keys.mkString(", ")}\n")
      }

      if (info.searchAttributes.nonEmpty) {
        output.append(s"   Search Attributes: ${info.searchAttributes.keys.mkString(", ")}\n")
      }

      output.append("\n")
    }

    if (result.hasMoreResults) {
      output.append("(More results available - use next page token)\n")
    }

    output.toString
  }

  def main(args:Array[String]):Unit = {
    log.info(s"args: '${args.mkString(",")}'")

    implicit val ec: ExecutionContext = ExecutionContext.global

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv,
      new ConfigurationArgs(args,"wf-temporal","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [mem://,dir://] (def: ${d.datastore})"),
        ArgString('e', "engine",s"Engine URI [temporal://] (def: ${d.engine})"),
        ArgString('w', "wf",s"Workflow implementation [demo://] (def: ${d.wf})"),

        ArgString('_', "por.owner.name",s"PoR owner name (def: ${d.porOwnerName})"),
        ArgString('_', "por.flow",s"PoR flow: flow-1|flow-2|flow-3|flow-4|flow-5 (def: ${d.porFlow})"),
        ArgString('_', "por.pol.signal-mode",s"PoL signal mode: file|rest|simulate (def: ${d.porPolSignalMode})"),
        ArgString('_', "por.tags",s"PoR workflow tags (comma-separated, e.g., CEX,Bybit) (def: ${d.porTags.mkString(",")})"),
        ArgString('_', "por.memo",s"PoR workflow memo (key=value pairs, comma-separated, e.g., region=US,env=prod) (def: ${d.porMemo.mkString(",")})"),

        ArgCmd("server",s"Start Workflow Schema REST server"),
        ArgCmd("temporal",s"Temporal subcommands"),
        ArgCmd("por-worker",s"Start PoR Temporal Worker"),
        ArgCmd("por-start",s"Start PoR Workflow: por-start [flow-N] [commit-file.json]"),

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

    def getStore(uri:String):WorkflowStore = {
      uri.split("://").toList match {
        case "mem" :: Nil => new WorkflowStoreMem()
        case "dir" :: Nil => new WorkflowStoreDir()
        case "dir" :: dir :: Nil => new WorkflowStoreDir(dir)
        case _ =>
          Console.err.println(s"Unknown DataStore: '${uri}'")
          sys.exit(1)
      }
    }

    val impl = config.wf.split("://").toList match {
      case "demo" :: Nil => new skel.wf.temporal.por.demo.PorActivitiesDemo()
      case _ => {
        Console.err.println(s"Unknown workflow implementation: '${config.wf}")
        sys.exit(1)
      }
    }

    log.info(s"Workflow: ${impl}")

    val r = config.cmd match {
      case "server" =>
        val store = getStore(config.datastore)
        Console.err.println(s"Store: ${store}")

        run(config.host, config.port, config.uri, c,
          Seq(
            (WorkflowRegistry(store, config.engine), "WorkflowRegistry", (reg, ac) => {
              new WorkflowRoutes(reg)(ac)
            })
          )
        )

      case "wf" =>
        // Server mode (future implementation)
        "Server mode not implemented yet"

      case "temporal" =>
        val futureResult = config.params.toList match {
          case "query" :: query :: Nil =>
            Temporal.query(config.engine, query)

          case "query" :: query :: pageSize :: Nil =>
            Temporal.query(config.engine, query, pageSize.toInt)

          case "get" :: runId :: Nil =>
            Temporal.get(config.engine, runId)

          case "describe" :: workflowId :: Nil =>
            Temporal.describe(config.engine, workflowId)

          case "describe" :: workflowId :: runId :: Nil =>
            Temporal.describe(config.engine, workflowId, Some(runId))

          case "list" :: Nil =>
            Temporal.list(config.engine)

          case "list" :: workflowType :: tail =>
            val pageSize = tail.headOption.map(_.toInt).getOrElse(10)
            Temporal.list(config.engine, workflowType = Some(workflowType), pageSize = pageSize)

          case "status" :: status :: tail =>
            val pageSize = tail.headOption.map(_.toInt).getOrElse(10)
            Temporal.list(config.engine, status = Some(status), pageSize = pageSize)

          case _ =>
            Temporal.list(config.engine)
        }

        Try(Await.result(futureResult, 30.seconds))

      case "por-worker" =>
        PorWorker.run(config.engine, impl)        

      case "por-start" =>
        
        // Load previous commit file if provided
        def load(commitFile:String):PorWorkflowRun = { 
          try {
              val json = os.read(os.Path(commitFile, os.pwd))
              val mapper = new com.fasterxml.jackson.databind.ObjectMapper()
              mapper.registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)
              val run = mapper.readValue(json, classOf[PorWorkflowRun])
              log.info(s"Loaded: $commitFile")
              run
            } catch {
              case e: Exception =>
                Console.err.println(s"Failed to load commit file: ${commitFile}: ${e.getMessage}")
                sys.exit(1)
            }
        }

        def generate(pooInput:Option[PooInput], porInput:Option[PorInput], polInput:Option[PolInput]):PorWorkflowRun = {
          // Create workflow input with step definitions
          val workflowInput = PorWorkflowInput(
            poo = pooInput.map(input => StepDef(input = Some(input))),
            por = porInput.map(input => StepDef(input = Some(input))),
            pol = polInput.map(input => StepDef(input = Some(input))),
            solvency = Some(StepDef()),
            report = Some(StepDef()),
            commit = Some(StepDef())
          )

          // Create workflow run context
          val workflowRun = PorWorkflowRun(
            ownerName = config.porOwnerName,
            ts0 = System.currentTimeMillis(),
            ts1 = System.currentTimeMillis(),
            tags = config.porTags,
            memo = config.porMemo,
            input = workflowInput,
            output = PorWorkflowOutput()
          )
          workflowRun
        }

        // Generate mock wallets for demo
        val mockWallets = DemoUtil.generateMockWallets()

        // Parse flow to determine required steps and create appropriate inputs
        val workflowRun = config.params.toList match {
          case "flow-1" :: f => // PoO -> PoR -> PoL -> Solvency -> Report
            if(f.size == 1) load(f.head)
            else 
              generate(
                Some(PooInput(mockWallets, "signature")),
                Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))),
                Some(PolInput("/tmp/liabilities.json", waitForConfirmation = true, config = Map("signalMode" -> config.porPolSignalMode)))
              )

          case "flow-2" :: f => // PoR -> PoL -> Solvency -> Report
            if(f.size == 1) load(f.head)
            else 
            generate(
              None,
              Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))),
              Some(PolInput("/tmp/liabilities.json", waitForConfirmation = true, config = Map("signalMode" -> config.porPolSignalMode)))
            )
          case "flow-3" :: f => // PoR -> Report
            if(f.size == 1) load(f.head)
            else 
            generate(
              None,
              Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))),
              None
            )
          case "flow-4" :: f => // PoO -> PoR -> Report
            if(f.size == 1) load(f.head)
            else 
            generate(
              Some(PooInput(mockWallets, "signature")),
              Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))),
              None
            )
          case "flow-5" :: f => // PoL only
            if(f.size == 1) load(f.head)
            else 
            generate(
              None,
              None,
              Some(PolInput("/tmp/liabilities.json", waitForConfirmation = true, config = Map("signalMode" -> config.porPolSignalMode)))
            )
          case f :: Nil =>
            load(f)
        }
        

        PorStarter.run(config.engine, workflowRun)        

      case _ =>
        s"Unknown command: ${config.cmd}"
    }

    Console.out.println(s"${r}")
  }
}



