package io.syspulse.skel.wf.ext.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import spray.json._

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf}
import io.hacken.ext.wf.WorkflowSchemaJson._
import io.hacken.ext.wf.WorkflowConfigJson._
import io.hacken.ext.wf.WorkflowGrafJson._
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}
import io.hacken.ext.detector.DetectorSchemaJson._
import io.hacken.ext.detector.DetectorConfigJson._

/**
 * `dir://` datastore. Backed by an in-memory [[WorkflowStoreMem]] for reads, with JSON
 * file persistence per object type under sub-directories:
 *   {dir}/schema/{id}.json
 *   {dir}/config/{id}.json
 *   {dir}/graf/{id}.json
 *   {dir}/detector-schema/{id}.json
 *   {dir}/detector-config/{id}.json
 *
 * Existing files are loaded on construction.
 */
class WorkflowStoreDir(dir: String = "store/") extends WorkflowStoreMem {
  override protected val log = Logger(getClass)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val DIR_SCHEMA  = "schema"
  val DIR_CONFIG  = "config"
  val DIR_GRAF    = "graf"
  val DIR_DSCHEMA = "detector-schema"
  val DIR_DCONFIG = "detector-config"

  private val root = os.Path(dir, os.pwd)

  private def sub(name: String): os.Path = root / name

  private def write(name: String, id: Int, json: String): Try[Unit] = Try {
    val d = sub(name)
    os.makeDir.all(d)
    os.write.over(d / s"${id}.json", json)
  }

  private def remove(name: String, id: Int): Try[Unit] = Try {
    val f = sub(name) / s"${id}.json"
    if (os.exists(f)) os.remove(f)
  }

  private def loadDir[T](name: String, parse: String => T, store: T => Unit): Int = {
    val d = sub(name)
    if (!os.exists(d)) return 0
    val files = os.list(d).filter(p => p.last.endsWith(".json"))
    var n = 0
    files.foreach { p =>
      Try(parse(os.read(p))) match {
        case Success(t) => store(t); n += 1
        case Failure(e) => log.warn(s"failed to load ${p}: ${e.getMessage}")
      }
    }
    log.info(s"loaded ${n} from ${d}")
    n
  }

  // ---------------------------------------------------------------- persisted overrides
  override def addSchema(s: WorkflowSchema): Future[WorkflowSchema] =
    write(DIR_SCHEMA, s.id, s.toJson.compactPrint).fold(Future.failed, _ => super.addSchema(s))
  override def delSchema(id: Int): Future[Int] =
    super.delSchema(id).map { r => remove(DIR_SCHEMA, id); r }

  override def addConfig(c: WorkflowConfig): Future[WorkflowConfig] =
    write(DIR_CONFIG, c.id, c.toJson.compactPrint).fold(Future.failed, _ => super.addConfig(c))
  override def delConfig(id: Int): Future[Int] =
    super.delConfig(id).map { r => remove(DIR_CONFIG, id); r }

  override def addGraf(g: WorkflowGraf): Future[WorkflowGraf] =
    super.addGraf(g).map { g1 => write(DIR_GRAF, g1.id, g1.toJson.compactPrint).get; g1 }
  override def delGraf(id: Int): Future[Int] =
    super.delGraf(id).map { r => remove(DIR_GRAF, id); r }

  override def addDetectorSchema(d: DetectorSchema): Future[DetectorSchema] =
    write(DIR_DSCHEMA, d.id, d.toJson.compactPrint).fold(Future.failed, _ => super.addDetectorSchema(d))
  override def delDetectorSchema(id: Int): Future[Int] =
    super.delDetectorSchema(id).map { r => remove(DIR_DSCHEMA, id); r }
  override def addDetectorConfig(d: DetectorConfig): Future[DetectorConfig] =
    write(DIR_DCONFIG, d.id, d.toJson.compactPrint).fold(Future.failed, _ => super.addDetectorConfig(d))
  override def delDetectorConfig(id: Int): Future[Int] =
    super.delDetectorConfig(id).map { r => remove(DIR_DCONFIG, id); r }

  // ---------------------------------------------------------------- initial load
  loadDir[WorkflowSchema](DIR_SCHEMA,   _.parseJson.convertTo[WorkflowSchema],  s => schemas = schemas + (s.id -> s))
  loadDir[WorkflowConfig](DIR_CONFIG,   _.parseJson.convertTo[WorkflowConfig],  c => configs = configs + (c.id -> c))
  loadDir[WorkflowGraf](DIR_GRAF,       _.parseJson.convertTo[WorkflowGraf],    g => grafs = grafs + (g.id -> g))
  loadDir[DetectorSchema](DIR_DSCHEMA,  _.parseJson.convertTo[DetectorSchema],  d => dSchemas = dSchemas + (d.id -> d))
  loadDir[DetectorConfig](DIR_DCONFIG,  _.parseJson.convertTo[DetectorConfig],  d => dConfigs = dConfigs + (d.id -> d))
}
