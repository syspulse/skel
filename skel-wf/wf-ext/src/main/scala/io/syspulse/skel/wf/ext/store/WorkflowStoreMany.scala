package io.syspulse.skel.wf.ext.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}

// ============================================================================
// WorkflowStoreMany
//
// Encapsulates MULTIPLE WorkflowStore-s under one WorkflowStore (see skel-dash
// DataSourceMany for the sibling pattern). Every method FOLDS over the underlying
// stores in order and returns the FIRST one whose Future succeeds - it does NOT
// continue to the remaining stores once one succeeds. The call fails ONLY when
// EVERY store failed (the errors are aggregated).
//
// NOTE on semantics: "success" = the underlying Future completed without an
// exception. A store that legitimately answers `None` / empty / 0 is therefore a
// success and stops the fold - so list the PRIMARY store first. This is a
// fallback/replica model (try each store until one responds), not a merge.
//
// The composite trait defaults (listSchemas, createConfigFromSchema, update*Status,
// next*Id, setup0) are inherited unchanged: they are built from the primitives
// below, so each of their sub-calls independently folds over the stores.
// ============================================================================
object WorkflowStoreMany {
  private val log = Logger(getClass)
  val PREFIX = "many://"

  /**
   * Build a WorkflowStoreMany from a `many://a,b,c` URI, creating each underlying store with `mk`
   * (typically the App's own store factory). A store that fails to construct is warned and skipped.
   */
  def resolve(uri: String, mk: String => WorkflowStore)(implicit ec: ExecutionContext): WorkflowStoreMany = {
    val stores = uri.stripPrefix(PREFIX).split(",").map(_.trim).filter(_.nonEmpty).flatMap { u =>
      Try(mk(u)) match {
        case Success(s) => Some(s)
        case Failure(e) =>
          log.warn(s"Failed to create WorkflowStore: '${u}': ${e.getMessage}")
          None
      }
    }.toSeq
    new WorkflowStoreMany(stores)
  }
}

class WorkflowStoreMany(val stores: Seq[WorkflowStore])(implicit ec: ExecutionContext) extends WorkflowStore {
  private val log = Logger(getClass)

  override def toString: String = s"WorkflowStoreMany(${stores.size} stores)"

  /**
   * Fold over the stores: try each in order, return the first Future that succeeds, and stop. Only
   * when ALL stores have failed does the result fail (with the aggregated messages, last error as cause).
   */
  private def firstSuccess[T](op: String)(f: WorkflowStore => Future[T]): Future[T] = {
    def loop(rest: List[WorkflowStore], idx: Int, errs: List[Throwable]): Future[T] = rest match {
      case Nil =>
        val msg = s"WorkflowStoreMany: all ${stores.size} store(s) failed for '${op}'"
        log.error(s"${msg}: ${errs.reverse.map(_.getMessage).mkString(" | ")}")
        Future.failed(new Exception(s"${msg}: ${errs.reverse.map(_.getMessage).mkString(" | ")}", errs.headOption.orNull))
      case store :: tail =>
        f(store).recoverWith { case e =>
          log.warn(s"WorkflowStoreMany: store[${idx}] failed for '${op}', trying next: ${e.getMessage}")
          loop(tail, idx + 1, e :: errs)
        }
    }
    if (stores.isEmpty) Future.failed(new Exception(s"WorkflowStoreMany: no stores configured (op='${op}')"))
    else loop(stores.toList, 0, Nil)
  }

  // ---------------------------------------------------------------- WorkflowSchema
  def addSchema(s: WorkflowSchema): Future[WorkflowSchema] = firstSuccess("addSchema")(_.addSchema(s))
  def getSchema(id: Int): Future[WorkflowSchema]           = firstSuccess(s"getSchema(${id})")(_.getSchema(id))
  def getSchemaOpt(id: Int): Future[Option[WorkflowSchema]] = firstSuccess(s"getSchemaOpt(${id})")(_.getSchemaOpt(id))
  def delSchema(id: Int): Future[Int]                      = firstSuccess(s"delSchema(${id})")(_.delSchema(id))
  def allSchemas: Future[Seq[WorkflowSchema]]              = firstSuccess("allSchemas")(_.allSchemas)
  def sizeSchemas: Future[Long]                            = firstSuccess("sizeSchemas")(_.sizeSchemas)

  // ---------------------------------------------------------------- WorkflowConfig
  def addConfig(c: WorkflowConfig): Future[WorkflowConfig]  = firstSuccess("addConfig")(_.addConfig(c))
  def getConfig(id: Int): Future[WorkflowConfig]            = firstSuccess(s"getConfig(${id})")(_.getConfig(id))
  def getConfigOpt(id: Int): Future[Option[WorkflowConfig]] = firstSuccess(s"getConfigOpt(${id})")(_.getConfigOpt(id))
  def delConfig(id: Int): Future[Int]                       = firstSuccess(s"delConfig(${id})")(_.delConfig(id))
  def allConfigs: Future[Seq[WorkflowConfig]]               = firstSuccess("allConfigs")(_.allConfigs)
  def sizeConfigs: Future[Long]                             = firstSuccess("sizeConfigs")(_.sizeConfigs)
  def findConfigByOid(oid: String): Future[Seq[WorkflowConfig]]     = firstSuccess(s"findConfigByOid(${oid})")(_.findConfigByOid(oid))
  def findConfigByXid(xid: String): Future[Option[WorkflowConfig]]  = firstSuccess(s"findConfigByXid(${xid})")(_.findConfigByXid(xid))

  // ---------------------------------------------------------------- WorkflowGraf
  def addGraf(g: WorkflowGraf): Future[WorkflowGraf]     = firstSuccess("addGraf")(_.addGraf(g))
  def getGraf(id: Int): Future[WorkflowGraf]             = firstSuccess(s"getGraf(${id})")(_.getGraf(id))
  def getGrafOpt(id: Int): Future[Option[WorkflowGraf]]  = firstSuccess(s"getGrafOpt(${id})")(_.getGrafOpt(id))
  def delGraf(id: Int): Future[Int]                      = firstSuccess(s"delGraf(${id})")(_.delGraf(id))
  def allGrafs: Future[Seq[WorkflowGraf]]                = firstSuccess("allGrafs")(_.allGrafs)
  def sizeGrafs: Future[Long]                            = firstSuccess("sizeGrafs")(_.sizeGrafs)

  // ---------------------------------------------------------------- DetectorSchema
  def addDetectorSchema(d: DetectorSchema): Future[DetectorSchema]     = firstSuccess("addDetectorSchema")(_.addDetectorSchema(d))
  def getDetectorSchema(id: Int): Future[Option[DetectorSchema]]       = firstSuccess(s"getDetectorSchema(${id})")(_.getDetectorSchema(id))
  def delDetectorSchema(id: Int): Future[Int]                          = firstSuccess(s"delDetectorSchema(${id})")(_.delDetectorSchema(id))
  def allDetectorSchemas: Future[Seq[DetectorSchema]]                  = firstSuccess("allDetectorSchemas")(_.allDetectorSchemas)
  def sizeDetectorSchemas: Future[Long]                                = firstSuccess("sizeDetectorSchemas")(_.sizeDetectorSchemas)

  // ---------------------------------------------------------------- DetectorConfig
  def addDetectorConfig(d: DetectorConfig): Future[DetectorConfig]  = firstSuccess("addDetectorConfig")(_.addDetectorConfig(d))
  def getDetectorConfig(id: Int): Future[Option[DetectorConfig]]    = firstSuccess(s"getDetectorConfig(${id})")(_.getDetectorConfig(id))
  def delDetectorConfig(id: Int): Future[Int]                       = firstSuccess(s"delDetectorConfig(${id})")(_.delDetectorConfig(id))
  def allDetectorConfigs: Future[Seq[DetectorConfig]]               = firstSuccess("allDetectorConfigs")(_.allDetectorConfigs)
  def sizeDetectorConfigs: Future[Long]                             = firstSuccess("sizeDetectorConfigs")(_.sizeDetectorConfigs)
}
