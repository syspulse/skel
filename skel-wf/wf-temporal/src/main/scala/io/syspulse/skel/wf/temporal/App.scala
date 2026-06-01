package io.syspulse.skel.wf.temporal

import scala.util.{Success, Try}
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.wf.temporal.por._
import io.syspulse.skel.wf.temporal.por.demo.DemoUtil
import io.syspulse.skel.wf.temporal.por2.Por2Schema
import io.syspulse.skel.wf.temporal.workflow.store._
import io.syspulse.skel.wf.temporal.workflow.server._
import io.syspulse.skel.wf.temporal.por.nul.PorActivitiesNull
import io.hacken.ext.wf.WorkflowRun
import io.hacken.ext.detector.DetectorConfig
import io.syspulse.skel.wf.temporal.workflow.{GenericStarter}

// Examples:
//   temporal init tid:Int pid:Int sys:Keyword proj:Keyword
//   temporal query "ExecutionStatus = 'Running'"
//   temporal query "WorkflowType = 'PorWorkflow' AND ExecutionStatus = 'Running'" 50
//   temporal describe por-workflow-Binance-1234567890
//   temporal list --status Running
//   temporal list --type PorWorkflow 25

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/wf",

  datastore:String = "test://",  // Pre-populated test store with Por2 and Demo configs
  engine:String = "temporal://",
  wf:String = "demo://",

  porProject: String = "DefaultProject",
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
        ArgString('w', "wf",s"Workflow implementation [demo://,null://] (def: ${d.wf})"),

        ArgString('_', "por.project",s"PoR project (def: ${d.porProject})"),
        ArgString('_', "por.flow",s"PoR flow: flow-1|flow-2|flow-3|flow-4|flow-5 (def: ${d.porFlow})"),
        ArgString('_', "por.pol.signal",s"PoL signal mode: file|api|simulate (def: ${d.porPolSignalMode})"),
        ArgString('_', "por.tags",s"PoR workflow tags (comma-separated, e.g., CEX,Bybit) (def: ${d.porTags.mkString(",")})"),
        ArgString('_', "por.memo",s"PoR workflow memo (key=value pairs, comma-separated, e.g., region=US,env=prod) (def: ${d.porMemo.mkString(",")})"),

        ArgCmd("server",s"Start Workflow Schema REST server"),
        ArgCmd("temporal",s"Temporal subcommands"),
        ArgCmd("por-worker",s"Start PoR Temporal Worker"),
        ArgCmd("por-start",s"Start PoR Workflow: por-start [flow-N] [commit-file.json]"),
        ArgCmd("por2-start",s"Start PoR2 Generic Workflow: por2-start [tenant-id] [project-id] [title]"),
        ArgCmd("demo-start",s"Start Demo Generic Workflow: demo-start [flow] [title]"),

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

      porProject = c.getString("por.project").getOrElse(d.porProject),
      porFlow = c.getString("por.flow").getOrElse(d.porFlow),
      porPolSignalMode = c.getString("por.pol.signal").getOrElse(d.porPolSignalMode),
      porTags = c.getListString("por.tags",d.porTags),
      porMemo = c.getMap("por.memo",d.porMemo),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    log.info(s"Config: ${config}")

    def getStore(uri:String):WorkflowSchemaStore = {
      uri.split("://").toList match {
        case "mem" :: Nil => new WorkflowSchemaStoreMem()
        case "test" :: Nil => new WorkflowSchemaStoreTest()  // Pre-populated with Por2 and Demo schemas
        case "dir" :: Nil => new WorkflowSchemaStoreDir()
        case "dir" :: dir :: Nil => new WorkflowSchemaStoreDir(dir)
        case _ =>
          Console.err.println(s"Unknown DataStore: '${uri}'")
          sys.exit(1)
      }
    }

    def getRunStore(uri:String):WorkflowRunStore = {
      uri.split("://").toList match {
        case "mem" :: Nil => new WorkflowRunStoreMem()
        case "test" :: Nil => new WorkflowRunStoreMem()
        case "dir" :: Nil => new WorkflowRunStoreDir("store/runs/")
        case "dir" :: dir :: Nil => new WorkflowRunStoreDir(dir)
        case _ =>
          Console.err.println(s"Unknown RunStore: '${uri}'")
          sys.exit(1)
      }
    }

    def getConfigStore(uri:String):WorkflowConfigStore = {
      uri.split("://").toList match {
        case "mem" :: Nil => new WorkflowConfigStoreMem()
        case "test" :: Nil => new WorkflowConfigStoreTest()  // Pre-populated with all Por2 and Demo configs
        case "dir" :: Nil => new WorkflowConfigStoreDir("store/configs/")
        case "dir" :: dir :: Nil => new WorkflowConfigStoreDir(dir)
        case _ =>
          Console.err.println(s"Unknown ConfigStore: '${uri}'")
          sys.exit(1)
      }
    }

    val impl = config.wf.split("://").toList match {
      case "demo" :: Nil => new skel.wf.temporal.por.demo.PorActivitiesDemo()
      case "null" :: Nil => new skel.wf.temporal.por.nul.PorActivitiesNull()
      case _ => {
        Console.err.println(s"Unknown workflow implementation: '${config.wf}")
        sys.exit(1)
      }
    }

    log.info(s"Workflow: ${impl}")

    val r = config.cmd match {
      case "server" =>
        val store = getStore(config.datastore)
        val runStore = getRunStore(config.datastore)
        val configStore = getConfigStore(config.datastore)  // Use shared/persistent config store
        Console.err.println(s"Store: ${store}")
        Console.err.println(s"RunStore: ${runStore}")
        Console.err.println(s"ConfigStore: ${configStore}")

        // Initialize Por2 schemas and configs
        Console.err.println(s"Initializing PoR2 schemas and configs...")
        val por2Schema = Por2Schema.buildSchema(schemaId = 1, tenantId = 1, projectId = 1)
        val por2Configs = Por2Schema.buildStepConfigs(tenantId = 1, projectId = 1)

        Try(Await.result(store.+(por2Schema), 10.seconds)) match {
          case Success(_) => Console.err.println(s"Initialized PoR2 schema: ${por2Schema.name}")
          case scala.util.Failure(e) => Console.err.println(s"Warning: Failed to initialize PoR2 schema: ${e.getMessage}")
        }

        por2Configs.foreach { config =>
          Try(Await.result(configStore.+(config), 10.seconds)) match {
            case Success(_) => Console.err.println(s"Initialized PoR2 config: ${config.name} (id=${config.id})")
            case scala.util.Failure(e) => Console.err.println(s"Warning: Failed to initialize config ${config.name}: ${e.getMessage}")
          }
        }

        // Start PorWorker (legacy)
        PorWorker.run(config.engine, impl) match {
          case Success(worker) =>
            Console.err.println(s"PorWorker started: ${worker}")
          case scala.util.Failure(e) =>
            Console.err.println(s"Failed to start PorWorker: ${e.getMessage}")
            sys.exit(1)
        }

        // Start PoR2 Worker on POR2_QUEUE
        import io.syspulse.skel.wf.temporal.por2.Por2Worker
        Por2Worker.run(config.engine, store, runStore, configStore) match {
          case Success(worker) =>
            Console.err.println(s"Por2Worker started on queue: ${Por2Worker.TASK_QUEUE}")
          case scala.util.Failure(e) =>
            Console.err.println(s"Failed to start Por2Worker: ${e.getMessage}")
            sys.exit(1)
        }

        // Start Demo Worker on DEMO_QUEUE
        import io.syspulse.skel.wf.temporal.demo.DemoWorker
        DemoWorker.run(config.engine, store, runStore, configStore) match {
          case Success(worker) =>
            Console.err.println(s"DemoWorker started on queue: ${DemoWorker.TASK_QUEUE}")
          case scala.util.Failure(e) =>
            Console.err.println(s"Failed to start DemoWorker: ${e.getMessage}")
            sys.exit(1)
        }

        // Start HTTP server
        run(config.host, config.port, config.uri, c,
          Seq(
            (WorkflowRegistry(store, runStore, configStore, config.engine), "WorkflowRegistry", (reg, ac) => {
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

          case "signal" :: runId :: signalName :: fileJson :: Nil =>
            val dataJson = os.read(os.Path(fileJson, os.pwd))
            // First get workflow info by run ID to extract workflow ID
            import spray.json._
            val data = dataJson.parseJson.asJsObject
            Temporal.get(config.engine, runId).flatMap { info =>
              Temporal.signal(config.engine, info.workflowId, Some(runId), signalName, data)
            }

          case "signal" :: workflowId :: runId :: signalName :: fileJson :: Nil =>
            val dataJson = os.read(os.Path(fileJson, os.pwd))
            // Signal with explicit workflow ID and run ID
            import spray.json._
            val data = dataJson.parseJson.asJsObject
            Temporal.signal(config.engine, workflowId, Some(runId), signalName, data)

          case "init" :: attributeSpecs if attributeSpecs.nonEmpty =>
            // Parse attribute specs as name:type pairs
            // Example: temporal init tid:Int pid:Int sys:Keyword
            val attributes = attributeSpecs.map { spec =>
              spec.split(":") match {
                case Array(name, attrType) => name -> attrType
                case _ =>
                  Console.err.println(s"Invalid attribute spec: $spec (expected format: name:type)")
                  Console.err.println(s"Valid types: Int, Long, Keyword, Text, Bool, Datetime, Double, KeywordList")
                  sys.exit(1)
              }
            }.toMap
            
            Temporal.registerSearchAttributes(config.engine, attributes)

          case _ =>
            Console.err.println(s"Unknown temporal command: ${config.params.toList}")
            sys.exit(1)
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

        // Parse flow to determine required steps and create appropriate inputs
        val workflowRun = config.params.toList match {
          case "flow-1" :: f => // PoO -> PoR -> PoL -> Solvency -> Report
            if(f.size == 1) load(f.head)
            else DemoUtil.generateFlowRun("flow-1", config.porProject, config.porTags, config.porMemo, config.porPolSignalMode)

          case "flow-2" :: f => // PoR -> PoL -> Solvency -> Report
            if(f.size == 1) load(f.head)
            else DemoUtil.generateFlowRun("flow-2", config.porProject, config.porTags, config.porMemo, config.porPolSignalMode)

          case "flow-3" :: f => // PoR -> Report
            if(f.size == 1) load(f.head)
            else DemoUtil.generateFlowRun("flow-3", config.porProject, config.porTags, config.porMemo, config.porPolSignalMode)

          case "flow-4" :: f => // PoO -> PoR -> Report
            if(f.size == 1) load(f.head)
            else DemoUtil.generateFlowRun("flow-4", config.porProject, config.porTags, config.porMemo, config.porPolSignalMode)

          case "flow-5" :: f => // PoL only
            if(f.size == 1) load(f.head)
            else DemoUtil.generateFlowRun("flow-5", config.porProject, config.porTags, config.porMemo, config.porPolSignalMode)

          case f :: Nil =>
            load(f)

          case _ => throw new IllegalArgumentException(s"Unknown flow: ${config.params.toList}")
        }
        

        val futureResult = PorStarter.run(config.engine, workflowRun)
        Try(Await.result(futureResult, 30.seconds)) match {
          case Success(result) => s"Workflow started: workflowId = ${result.workflowId}, runId = ${result.runId}"
          case scala.util.Failure(e) => s"Failed to start workflow: ${e.getMessage}"
        }

      case "por2-start" =>
        val DEF_TITLE = "PoR-{project}-{ts}"
        // Parse flow type, tenant ID, project ID, and optional title
        val (flow, tenantId, projectId, title) = config.params.toList match {
          case f :: tid :: pid :: titleParam :: Nil if f.startsWith("flow-") => (f, tid.toInt, pid.toInt, titleParam)
          case f :: tid :: pid :: Nil if f.startsWith("flow-") => (f, tid.toInt, pid.toInt, DEF_TITLE)
          case f :: tid :: Nil if f.startsWith("flow-") => (f, tid.toInt, 1, DEF_TITLE)
          case f :: Nil if f.startsWith("flow-") => (f, 1, 1, DEF_TITLE)
          case tid :: pid :: titleParam :: Nil => ("flow-1", tid.toInt, pid.toInt, titleParam)
          case tid :: pid :: Nil => ("flow-1", tid.toInt, pid.toInt, DEF_TITLE)
          case tid :: Nil => ("flow-1", tid.toInt, 1, DEF_TITLE)
          case Nil => ("flow-1", 1, 1, DEF_TITLE)
          case _ =>
            Console.err.println(s"Invalid arguments: ${config.params.toList}")
            Console.err.println(s"Usage: por2-start [flow-type] [tenant-id] [project-id] [title]")
            Console.err.println(s"  flow-type: flow-1, flow-2, flow-3, flow-4, flow-5 (default: flow-1)")
            Console.err.println(s"    flow-1: PoO -> PoR -> PoL -> Solvency -> Report -> Commit")
            Console.err.println(s"    flow-2: PoR -> PoL -> Solvency -> Report -> Commit")
            Console.err.println(s"    flow-3: PoR -> Report -> Commit")
            Console.err.println(s"    flow-4: PoO -> PoR -> Report -> Commit")
            Console.err.println(s"    flow-5: PoL only")            
            sys.exit(1)
        }

        log.info(s"Starting PoR2 Generic Workflow: flow=$flow, tenantId=$tenantId, projectId=$projectId, title='$title'")

        // Initialize stores - use same stores as server
        val schemaStore = getStore(config.datastore)
        val runStore = getRunStore(config.datastore)
        val configStore = getConfigStore(config.datastore)

        // Create schema and configs with custom title (can be used as workflow ID template)
        val schema = Por2Schema.buildSchema(schemaId = 1, tenantId = tenantId, projectId = projectId, title = title)
        val allConfigs = Por2Schema.buildStepConfigs(tenantId = tenantId, projectId = projectId)

        // Get step IDs for the selected flow
        val flowStepIds = Por2Schema.getFlowSteps(flow)
        val configs = allConfigs.filter(c => flowStepIds.contains(c.id))

        log.info(s"Flow $flow includes steps: ${configs.map(_.name).mkString(" -> ")}")

        // Store schema
        Try(Await.result(schemaStore.+(schema), 10.seconds)) match {
          case Success(_) => log.info(s"Stored schema: ${schema.name}")
          case scala.util.Failure(e) =>
            Console.err.println(s"Failed to store schema: ${e.getMessage}")
            sys.exit(1)
        }

        // Store ALL configs (not just flow-filtered ones) so they can be referenced
        allConfigs.foreach { config =>
          Try(Await.result(configStore.+(config), 10.seconds)) match {
            case Success(_) => log.debug(s"Stored config: ${config.name} (id=${config.id})")
            case scala.util.Failure(e) =>
              Console.err.println(s"Failed to store config: ${e.getMessage}")
              sys.exit(1)
          }
        }

        // Create WorkflowRun with dynamic ID from template
        val context = Map(
          "tid" -> tenantId.toString,
          "pid" -> projectId.toString,
          "project" -> config.porProject
        )
        val workflowId = io.hacken.ext.wf.WorkflowIdGenerator.generateFromSchema(
          schema = schema,
          context = context,
          defaultTemplate = "por2-{project}-{ts}"
        )        

        // Build workflow steps with metadata (only for selected flow)
        val workflowSteps = configs.map { c =>
          io.hacken.ext.wf.WorkflowStep(
            id = c.id,
            name = c.name,
            typ = DetectorConfig.getString(c, "type", "AUTO")
          )
        }

        val workflowRun = WorkflowRun(
          wid = workflowId,
          rid = None,
          status = "NEW",
          cursor = -1,
          schema = schema.id,
          steps = workflowSteps
        )

        log.info(s"Created WorkflowRun: wid=${workflowRun.wid}, steps=${workflowRun.steps.map(s => s"${s.id}:${s.name}").mkString(",")}")

        // Start workflow with schema name as workflow type on POR2_QUEUE
        import io.syspulse.skel.wf.temporal.por2.Por2Worker
        val futureResult: Future[WorkflowRun] = GenericStarter.run(config.engine, workflowRun, schema.name, Por2Worker.TASK_QUEUE)
        Try(Await.result(futureResult, 30.seconds)) match {
          case Success(result) =>
            s"PoR2 Workflow started:\n" +
            s"  Workflow ID: ${result.wid}\n" +
            s"  Run ID: ${result.rid}\n" +
            s"  Task Queue: ${Por2Worker.TASK_QUEUE}\n" +
            s"  Steps: ${configs.map(_.name).mkString(" → ")}\n" +
            s"  Query: temporal workflow show -w ${result.wid}"
          case scala.util.Failure(e) =>
            s"Failed to start PoR2 workflow: ${e.getMessage}"
        }

      case "demo-start" =>
        // Parse flow string and optional title from parameters
        val (flowStr, title) = config.params.toList match {
          case flow :: titleParam :: Nil => (flow, titleParam)
          case flow :: Nil => (flow, "demo-{ts}")  // Default title with timestamp          
          case _ =>
            Console.err.println(s"Invalid arguments: ${config.params.toList}")            
            sys.exit(1)
        }

        log.info(s"Starting Demo Workflow: flow='$flowStr', title='$title'")

        // Initialize stores - use same stores as server
        val schemaStore = getStore(config.datastore)
        val runStore = getRunStore(config.datastore)
        val configStore = getConfigStore(config.datastore)

        // Parse flow string and create schema
        val stepNames = flowStr.split("->").map(_.trim).toSeq
        val schema = io.syspulse.skel.wf.temporal.demo.DemoSchema.buildSchemaFromFlow(
          flowStr = flowStr,
          schemaId = 1,
          title = title
        )
        val configs = io.syspulse.skel.wf.temporal.demo.DemoSchema.buildStepConfigs(stepNames)

        log.info(s"Demo workflow: ${configs.map(_.name).mkString(" -> ")}")

        // Store schema
        Try(Await.result(schemaStore.+(schema), 10.seconds)) match {
          case Success(_) => log.info(s"Stored schema: ${schema.name}")
          case scala.util.Failure(e) =>
            Console.err.println(s"Failed to store schema: ${e.getMessage}")
            sys.exit(1)
        }

        // Store configs
        configs.foreach { config =>
          Try(Await.result(configStore.+(config), 10.seconds)) match {
            case Success(_) => log.debug(s"Stored config: ${config.name} (id=${config.id})")
            case scala.util.Failure(e) =>
              Console.err.println(s"Failed to store config: ${e.getMessage}")
              sys.exit(1)
          }
        }

        // Generate workflow ID
        val context = Map.empty[String, String]
        val workflowId = io.hacken.ext.wf.WorkflowIdGenerator.generateFromSchema(
          schema = schema,
          context = context
        )        

        // Build workflow steps
        val workflowSteps = configs.map { c =>
          io.hacken.ext.wf.WorkflowStep(
            id = c.id,
            name = c.name,
            typ = DetectorConfig.getString(c, "type", "AUTO")
          )
        }

        val workflowRun = WorkflowRun(
          wid = workflowId,
          rid = None,
          status = "NEW",
          cursor = -1,
          schema = schema.id,
          steps = workflowSteps
        )

        log.info(s"Created WorkflowRun: wid=${workflowRun.wid}, steps=${workflowRun.steps.map(s => s"${s.id}:${s.name}").mkString(",")}")

        // Start workflow on DEMO_QUEUE
        import io.syspulse.skel.wf.temporal.demo.DemoWorker
        val futureResult: Future[WorkflowRun] = GenericStarter.run(config.engine, workflowRun, schema.name, DemoWorker.TASK_QUEUE)
        Try(Await.result(futureResult, 30.seconds)) match {
          case Success(result) =>
            s"Demo Workflow started:\n" +
            s"  Workflow ID: ${result.wid}\n" +
            s"  Run ID: ${result.rid}\n" +
            s"  Task Queue: ${DemoWorker.TASK_QUEUE}\n" +
            s"  Flow: $flowStr\n" +
            s"  Steps: ${configs.map(_.name).mkString(" → ")}\n" +
            s"  Query: temporal workflow show -w ${result.wid}"
          case scala.util.Failure(e) =>
            s"Failed to start Demo workflow: ${e.getMessage}"
        }

      case _ =>
        s"Unknown command: ${config.cmd}"
    }

    Console.out.println(s"${r}")
  }
}



