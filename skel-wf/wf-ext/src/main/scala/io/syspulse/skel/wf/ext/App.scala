package io.syspulse.skel.wf.ext

import scala.util.{Success, Failure, Try}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

import io.syspulse.skel
import io.syspulse.skel.config._

import io.syspulse.skel.wf.ext.store.{WorkflowStore, WorkflowStoreMem, WorkflowStoreDir, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server.WorkflowRoutes
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL
import io.syspulse.skel.wf.ext.engine.{Engine, EngineMapper, EngineWorkflow, EngineStatus, WorkflowRuntimeView, TrackMapper}
import io.hacken.ext.wf.WorkflowConfig
import io.hacken.ext.detector.DetectorConfig

case class Config(
  host: String = "0.0.0.0",
  port: Int = 8080,
  uri: String = "/api/v1/wf/ext",

  datastore: String = "mem://",

  // Assembly DSL options
  wid: Option[Int] = None,    // --wid : WorkflowSchema id (default: next, starting at 0)
  wn: Option[String] = None,  // --wn  : WorkflowSchema name (default: random)

  // Engine options
  wf: Option[String] = None,  // --wf  : Engine URI (e.g. temporal://127.0.0.1:7233/default)
  ns: Option[String] = None,  // --ns  : Engine namespace override (e.g. default, '*')
  poll: Long = 3000,          // --poll: assembly-track polling interval in msec (def: 3000)

  cmd: String = "server",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {

  /**
   * Normalize a link-DSL pipeline. Accepts the bracket shorthand from the requirements
   * (`[PoO] -> [PoR] -> [Report]`) and rewrites each bare/bracketed token into the
   * Assembly DSL `Detector.<name>` form. Tokens already carrying an entity keyword
   * (`Detector.` / `Schema.`) or link-ids are passed through unchanged.
   */
  def normalizePipeline(pipeline: String): String =
    pipeline.split("->").map(_.trim).filter(_.nonEmpty).map { tok0 =>
      val tok = tok0.stripPrefix("[").stripSuffix("]").trim
      val lower = tok.toLowerCase
      if (lower.contains("detector") || lower.contains("schema")) tok
      else s"${AssemblyDSL.ENTITY_DETECTOR}.${tok}"
    }.mkString(" -> ")

  // Known CLI commands. The shared arg parser only recognises a command when it is the
  // first non-option token, so we hoist it to the front - this lets options precede the
  // command (e.g. `--wf=temporal:// assembly-link <rid> <pipeline>` as in the requirements).
  private val KNOWN_CMDS = Set("server", "schema", "assembly", "assembly-link", "assembly-track", "runtime-get")

  // Commands that take an Assembly DSL pipeline (which contains `->` tokens and spaces).
  private val DSL_CMDS = Set("schema", "assembly", "assembly-link", "assembly-track")

  /** A token that the arg parser treats as an option: `-x` / `--name[=..]`. `->` is NOT an option. */
  private def isOption(tok: String): Boolean = tok.matches("^-{1,2}[A-Za-z].*")

  /**
   * Normalize argv before handing it to the shared arg parser:
   *   1. hoist the command token to the front (so options may precede it);
   *   2. for DSL commands, merge the split pipeline tokens back into a SINGLE param.
   *
   * `run-app.sh` forwards args unquoted, so a quoted pipeline like '[PoO] -> [PoR]' arrives
   * word-split as `[PoO] -> [PoR]`; without step 2 the bare `->` tokens are rejected as
   * unknown options. assembly-link keeps its first param (runtimeId) separate.
   */
  private def preprocess(argv: Array[String]): Array[String] = {
    val idx = argv.indexWhere(KNOWN_CMDS.contains)
    if (idx < 0) return argv
    val cmd  = argv(idx)
    val rest = (argv.take(idx) ++ argv.drop(idx + 1)).toSeq
    if (!DSL_CMDS.contains(cmd)) return (cmd +: rest).toArray

    val (opts, params) = rest.partition(isOption)
    val mergedParams = cmd match {
      case "assembly-link" | "assembly-track" => params.toList match {
        case rid :: Nil        => Seq(rid)
        case rid :: pipeline   => Seq(rid, pipeline.mkString(" "))
        case Nil               => Seq()
      }
      case _ => if (params.isEmpty) Seq() else Seq(params.mkString(" "))
    }
    (cmd +: (opts ++ mergedParams)).toArray
  }

  // ANSI colors are a RENDERING concern only (App command output) - never stored on model fields.
  // Map a runtime status to an ANSI (foreground;background) code. Dark grey foreground
  // (256-color palette) is used on light backgrounds where black is hard to read.
  private val DARK_GREY = "38;5;238"
  private val statusAnsi: Map[String, String] = Map(
    //                         fg;bg
    EngineStatus.RUNNING    -> "97;44",            // white on blue
    EngineStatus.COMPLETED  -> s"${DARK_GREY};42", // dark grey on green
    EngineStatus.FAILED     -> "97;41",            // white on red
    EngineStatus.TERMINATED -> s"${DARK_GREY};43", // dark grey on yellow
    EngineStatus.CANCELED   -> s"${DARK_GREY};47", // dark grey on white
  )

  /** Wrap `text` in ANSI color codes for a runtime status (no-op when the status has no color). */
  def colorize(text: String, status: String): String = statusAnsi.get(status) match {
    case Some(code) => s"[${code}m${text}[0m"
    case None           => text
  }

  def main(argv: Array[String]): Unit = {
    val args = preprocess(argv)
    log.info(s"args: '${args.mkString(",")}'")
    implicit val ec: ExecutionContext = ExecutionContext.global

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv,
      new ConfigurationArgs(args, "wf-ext", "",
        ArgString('h', "http.host", s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port", s"listen port (def: ${d.port})"),
        ArgString('u', "http.uri", s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore", s"Datastore [mem://,dir://] (def: ${d.datastore})"),

        ArgString('_', "wid", s"WorkflowSchema id for schema/assembly (def: next id, starts at 0)"),
        ArgString('_', "wn", s"WorkflowSchema name for schema/assembly (def: random)"),

        ArgString('_', "wf", s"Engine URI (e.g. temporal://127.0.0.1:7233/default)"),
        ArgString('_', "ns", s"Engine namespace override (e.g. default, '*' for all)"),
        ArgString('_', "poll", s"assembly-track polling interval in msec (def: ${d.poll})"),

        ArgCmd("server", s"Start Workflow REST server"),
        ArgCmd("schema", s"Create a WorkflowSchema from an Assembly DSL pipeline (param: pipeline)"),
        ArgCmd("assembly", s"Create a WorkflowConfig (+ WorkflowSchema) from an Assembly DSL pipeline (param: pipeline)"),
        ArgCmd("assembly-link", s"Assemble a WorkflowConfig from DSL and link it to an Engine runtime (params: <runtimeId> <pipeline>)"),
        ArgCmd("assembly-track", s"assembly-link + poll the Engine runtime, rendering topology + step statuses (params: <runtimeId> <pipeline>)"),
        ArgCmd("runtime-get", s"Get Engine runtime workflow(s) (param: optional <runtimeId>); requires --wf"),

        ArgParam("<params>", "DSL pipeline, e.g. 'Detector.a -> Detector.b -> Detector.c'"),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),
      wid = c.getString("wid").map(_.toInt),
      wn = c.getString("wn"),
      wf = c.getString("wf").filter(_.nonEmpty),
      ns = c.getString("ns").filter(_.nonEmpty),
      poll = c.getString("poll").map(_.toLong).getOrElse(d.poll),
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    log.info(s"Config: ${config}")

    def getStore(uri: String): WorkflowStore = uri.split("://|/").toList match {
      case "mem" :: Nil | "cache" :: Nil => new WorkflowStoreMem()
      case "dir" :: Nil               => new WorkflowStoreDir("store/")
      case "dir" :: dir               => new WorkflowStoreDir(dir.mkString("/"))
      case _ =>
        Console.err.println(s"Unknown datastore: '${uri}', using mem://")
        new WorkflowStoreMem()
    }

    val store = getStore(config.datastore)

    // Engine URI: explicit --wf, or defaulted to temporal:// for the engine-only commands.
    def engineUri(default: String = "temporal://"): String = config.wf.getOrElse(default)
    def newEngine(): Engine = Engine(engineUri())

    // Render an EngineWorkflow tree for CLI output.
    def renderWorkflow(w: EngineWorkflow, indent: String = ""): String = {
      val times = (w.startedAt.map(t => s" start=${t}").getOrElse("")) + (w.closedAt.map(t => s" close=${t}").getOrElse(""))
      val head = s"${indent}${colorize(s"[${w.status}]", w.status)} ${w.name} wid=${w.id} rid=${w.runtimeId} ns=${w.namespace}${times}"
      val acts = w.activities.map(a => s"${indent}  - ${colorize(s"(${a.status})", a.status)} ${a.kind} ${a.name} id=${a.id}").mkString("\n")
      val kids = w.children.map(c => renderWorkflow(c, indent + "  ")).mkString("\n")
      Seq(head, acts, kids).filter(_.nonEmpty).mkString("\n")
    }

    // Assemble a WorkflowConfig from a DSL pipeline and link it to an Engine runtime by xid.
    def assembleLink(runtimeId: String, rest: Seq[String]): scala.concurrent.Future[WorkflowConfig] = {
      val pipeline = normalizePipeline(rest.mkString(" "))
      for {
        res    <- AssemblyDSL.assemble(pipeline, store, config.wid, config.wn)
        cfg0    = res.config.getOrElse(throw new Exception("assembly did not produce a WorkflowConfig"))
        linked  = cfg0.copy(xid = Some(runtimeId), updatedAt = System.currentTimeMillis())
        saved  <- store.addConfig(linked)
        _       = log.info(saved.toString) // log the xid-linked WorkflowConfig (raw toString)
      } yield saved
    }

    // Resolve cid -> DetectorConfig for a config's graph nodes (for status correlation by name).
    def loadDetectors(cfg: WorkflowConfig): scala.concurrent.Future[Map[Int, DetectorConfig]] = {
      val cids = cfg.graph.nodes.values.flatMap(_.cid).toSet.toSeq
      scala.concurrent.Future.sequence(cids.map(id => store.getDetectorConfig(id).map(_.map(id -> _))))
        .map(_.flatten.toMap)
    }

    def ts(): String = System.currentTimeMillis().toString

    // Render one tracking poll as a SINGLE line:
    //   {WorkflowConfig.name},{WorkflowConfig.xid},{WorkflowConfig.status}: [{step.name},{step.status}] -> ...
    // Only the `status` tokens are colored.
    def renderTrack(cfg: WorkflowConfig, view: WorkflowRuntimeView): String = {
      val steps = view.steps.map(s => s"[${s.name},${colorize(s.status, s.status)}]").mkString(" -> ")
      s"${ts()}: [${cfg.name},${cfg.xid.getOrElse("")},${colorize(view.status, view.status)}]: ${steps}"
    }

    val r = config.cmd match {
      case "server" =>
        Console.err.println(s"Store: ${store}")
        // Engine is created only when --wf is provided; engine REST routes are enabled then.
        val engine: Option[Engine] = config.wf.map(Engine(_))
        Console.err.println(s"Engine: ${engine.map(_ => engineUri()).getOrElse("(none, set --wf to enable /engine API)")}")
        // skel Server.parseUriPath only uses 3 path segments (api/v1/wf) for the prefix and drops
        // the 4th ("ext"), so re-add it via Routeable.withSuffix -> /api/v1/wf/ext/{schema,config,graf,engine}
        run(config.host, config.port, config.uri, c,
          Seq(
            (WorkflowRegistry(store), "WorkflowRegistry", (actor, ac) => new WorkflowRoutes(actor, engine)(ac).withSuffix("ext"))
          )
        )
        s"Server: http://${config.host}:${config.port}${config.uri}"

      case "schema" =>
        val pipeline = normalizePipeline(config.params.mkString(" "))
        val f = AssemblyDSL.buildSchema(pipeline, store, config.wid, config.wn)
        Try(Await.result(f, 30.seconds)) match {
          case Success(res) =>
            s"WorkflowSchema created: id=${res.schema.id}, name='${res.schema.name}', " +
              s"nodes=${res.schema.graph.nodes.size}, links=${res.schema.graph.links.size}, " +
              s"DetectorSchemas=${res.detectorSchemas.map(d => s"${d.id}:${d.name}").mkString(",")}"
          case Failure(e) => s"Failed to create WorkflowSchema: ${e.getMessage}"
        }

      case "assembly" =>
        val pipeline = normalizePipeline(config.params.mkString(" "))
        val f = AssemblyDSL.assemble(pipeline, store, config.wid, config.wn)
        Try(Await.result(f, 30.seconds)) match {
          case Success(res) =>
            s"WorkflowConfig assembled: configId=${res.config.map(_.id).getOrElse(-1)}, schemaId=${res.schema.id}, " +
              s"name='${res.schema.name}', nodes=${res.schema.graph.nodes.size}, links=${res.schema.graph.links.size}, " +
              s"DetectorSchemas=[${res.detectorSchemas.map(d => s"${d.id}:${d.name}").mkString(",")}], " +
              s"DetectorConfigs=[${res.detectorConfigs.map(d => s"${d.id}:${d.name}").mkString(",")}]"
          case Failure(e) => s"Failed to assemble WorkflowConfig: ${e.getMessage}"
        }

      case "runtime-get" =>
        val engine = newEngine()
        try {
          val out = config.params.headOption match {
            case Some(runtimeId) =>
              // single runtime instance, fully expanded (activities + child workflows)
              Await.result(engine.getRuntime(config.ns, runtimeId), 60.seconds) match {
                case Some(w) => s"Runtime ${runtimeId}:\n${renderWorkflow(w)}"
                case None    => s"Runtime not found: ${runtimeId} (ns=${config.ns.getOrElse("<all>")})"
              }
            case None =>
              // all runtimes (summary)
              val ws = Await.result(engine.getRuntimes(config.ns), 60.seconds)
              s"Runtimes (${ws.size}):\n" + ws.map(w => s"  ${colorize(s"[${w.status}]", w.status)} ${w.name} wid=${w.id} rid=${w.runtimeId} ns=${w.namespace}").mkString("\n")
          }
          out
        } catch {
          case e: Exception => s"Failed runtime-get: ${e.getMessage}"
        } finally engine.close()

      case "assembly-link" =>
        config.params.toList match {
          case runtimeId :: rest if rest.nonEmpty =>
            Try(Await.result(assembleLink(runtimeId, rest), 30.seconds)) match {
              case Success(c) =>
                s"WorkflowConfig linked: configId=${c.id}, xid=${c.xid.getOrElse("")}, " +
                  s"name='${c.name}', nodes=${c.graph.nodes.size}, links=${c.graph.links.size}"
              case Failure(e) => s"Failed assembly-link: ${e.getMessage}"
            }
          case _ =>
            s"Usage: assembly-link <runtimeId> <pipeline>  (e.g. assembly-link 019e7473-... '[PoO] -> [PoR] -> [Report]')"
        }

      case "assembly-track" =>
        config.params.toList match {
          case id :: rest if rest.nonEmpty =>
            val engine = newEngine()
            // modular resolution: UUID -> track a fixed run (RunId); else -> track latest run of a WorkflowId
            val mapper = TrackMapper.of(id)
            try {
              // assemble the WorkflowConfig from the DSL (persisted; name/xid bound below from the runtime)
              Try(Await.result(AssemblyDSL.assemble(normalizePipeline(rest.mkString(" ")), store, config.wid, config.wn), 30.seconds)) match {
                case Success(res) =>
                  var cfg = res.config.getOrElse(throw new Exception("assembly did not produce a WorkflowConfig"))
                  val detectors = Await.result(loadDetectors(cfg), 30.seconds)
                  Console.err.println(s"Tracking by ${mapper.kind}=${mapper.key} configId=${cfg.id} poll=${config.poll}ms")
                  // poll indefinitely, one status line per poll
                  while (true) {
                    Try(Await.result(mapper.resolve(engine, config.ns), 60.seconds)) match {
                      case Success(Some(w)) =>
                        // bind the WorkflowConfig to the resolved runtime:
                        //   name / meta.wid <- Temporal WorkflowId (w.id)
                        //   xid             <- Temporal RunId (w.runtimeId); reassigned on restart
                        val meta = cfg.meta.getOrElse(Map.empty[String, Any]) + ("wid" -> w.id)
                        val changed = cfg.name != w.id || !cfg.xid.contains(w.runtimeId) || cfg.meta != Some(meta)
                        if (changed) {
                          cfg = cfg.copy(name = w.id, xid = Some(w.runtimeId), meta = Some(meta), updatedAt = System.currentTimeMillis())
                          Await.result(store.addConfig(cfg), 30.seconds)
                          log.info(cfg.toString)
                        }
                        Console.out.println(renderTrack(cfg, EngineMapper.map(w, Some(cfg), detectors)))
                      case Success(None) =>
                        Console.out.println(s"${ts()}: [${cfg.name},${cfg.xid.getOrElse(mapper.key)},${EngineStatus.UNKNOWN}]: (Workflow Runtime not found)")
                      case Failure(e) =>
                        Console.out.println(s"${ts()}: [${cfg.name},${cfg.xid.getOrElse(mapper.key)},${EngineStatus.UNKNOWN}]: (poll error: ${e.getMessage})")
                    }
                    Thread.sleep(config.poll)
                  }
                  ""
                case Failure(e) => s"Failed assembly-track: ${e.getMessage}"
              }
            } finally engine.close()
          case _ =>
            s"Usage: assembly-track <workflowId|runtimeId> <pipeline>  " +
              "(e.g. assembly-track PoR-DefaultProject-... '[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]')"
        }

      case x =>
        s"Unknown command: '${x}'"
    }

    Console.out.println(s"${r}")
  }
}
