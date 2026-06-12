package io.syspulse.skel.wf.ext

import scala.util.{Success, Failure, Try}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

import io.syspulse.skel
import io.syspulse.skel.config._

import io.syspulse.skel.wf.ext.store.{WorkflowStore, WorkflowStoreMem, WorkflowStoreDir, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server.WorkflowRoutes
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL

case class Config(
  host: String = "0.0.0.0",
  port: Int = 8080,
  uri: String = "/api/v1/wf/ext",

  datastore: String = "mem://",

  // Assembly DSL options
  wid: Option[Int] = None,    // --wid : WorkflowSchema id (default: next, starting at 0)
  wn: Option[String] = None,  // --wn  : WorkflowSchema name (default: random)

  cmd: String = "server",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {

  def main(args: Array[String]): Unit = {
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

        ArgCmd("server", s"Start Workflow REST server"),
        ArgCmd("schema", s"Create a WorkflowSchema from an Assembly DSL pipeline (param: pipeline)"),
        ArgCmd("assembly", s"Create a WorkflowConfig (+ WorkflowSchema) from an Assembly DSL pipeline (param: pipeline)"),

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

    val r = config.cmd match {
      case "server" =>
        Console.err.println(s"Store: ${store}")
        // skel Server.parseUriPath only uses 3 path segments (api/v1/wf) for the prefix and drops
        // the 4th ("ext"), so re-add it via Routeable.withSuffix -> /api/v1/wf/ext/{schema,config,graf}
        run(config.host, config.port, config.uri, c,
          Seq(
            (WorkflowRegistry(store), "WorkflowRegistry", (actor, ac) => new WorkflowRoutes(actor)(ac).withSuffix("ext"))
          )
        )
        s"Server: http://${config.host}:${config.port}${config.uri}"

      case "schema" =>
        val pipeline = config.params.mkString(" ")
        val f = AssemblyDSL.buildSchema(pipeline, store, config.wid, config.wn)
        Try(Await.result(f, 30.seconds)) match {
          case Success(res) =>
            s"WorkflowSchema created: id=${res.schema.id}, name='${res.schema.name}', " +
              s"nodes=${res.schema.graph.nodes.size}, links=${res.schema.graph.links.size}, " +
              s"DetectorSchemas=${res.detectorSchemas.map(d => s"${d.id}:${d.name}").mkString(",")}"
          case Failure(e) => s"Failed to create WorkflowSchema: ${e.getMessage}"
        }

      case "assembly" =>
        val pipeline = config.params.mkString(" ")
        val f = AssemblyDSL.assemble(pipeline, store, config.wid, config.wn)
        Try(Await.result(f, 30.seconds)) match {
          case Success(res) =>
            s"WorkflowConfig assembled: configId=${res.config.map(_.id).getOrElse(-1)}, schemaId=${res.schema.id}, " +
              s"name='${res.schema.name}', nodes=${res.schema.graph.nodes.size}, links=${res.schema.graph.links.size}, " +
              s"DetectorSchemas=[${res.detectorSchemas.map(d => s"${d.id}:${d.name}").mkString(",")}], " +
              s"DetectorConfigs=[${res.detectorConfigs.map(d => s"${d.id}:${d.name}").mkString(",")}]"
          case Failure(e) => s"Failed to assemble WorkflowConfig: ${e.getMessage}"
        }

      case x =>
        s"Unknown command: '${x}'"
    }

    Console.out.println(s"${r}")
  }
}
