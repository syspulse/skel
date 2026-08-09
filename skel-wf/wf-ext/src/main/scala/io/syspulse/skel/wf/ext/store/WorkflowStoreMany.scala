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
// The composite trait defaults (listWSchemas, createWConfFromWSchema, update*Status,
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
  def addWSchema(wschema: WorkflowSchema): Future[WorkflowSchema] = firstSuccess("addWSchema")(_.addWSchema(wschema))
  def getWSchema(id: Int): Future[WorkflowSchema]           = firstSuccess(s"getWSchema(${id})")(_.getWSchema(id))
  def getWSchemaOpt(id: Int): Future[Option[WorkflowSchema]] = firstSuccess(s"getWSchemaOpt(${id})")(_.getWSchemaOpt(id))
  def delWSchema(id: Int): Future[Int]                      = firstSuccess(s"delWSchema(${id})")(_.delWSchema(id))
  def allWSchemas: Future[Seq[WorkflowSchema]]              = firstSuccess("allWSchemas")(_.allWSchemas)
  def sizeWSchemas: Future[Long]                            = firstSuccess("sizeWSchemas")(_.sizeWSchemas)

  // ---------------------------------------------------------------- WorkflowConfig
  def addWConf(wconf: WorkflowConfig): Future[WorkflowConfig]  = firstSuccess("addWConf")(_.addWConf(wconf))
  def getWConf(id: Int): Future[WorkflowConfig]            = firstSuccess(s"getWConf(${id})")(_.getWConf(id))
  def getWConfOpt(id: Int): Future[Option[WorkflowConfig]] = firstSuccess(s"getWConfOpt(${id})")(_.getWConfOpt(id))
  def delWConf(id: Int): Future[Int]                       = firstSuccess(s"delWConf(${id})")(_.delWConf(id))
  def allWConfs: Future[Seq[WorkflowConfig]]               = firstSuccess("allWConfs")(_.allWConfs)
  def sizeWConfs: Future[Long]                             = firstSuccess("sizeWConfs")(_.sizeWConfs)
  def findWConfByOid(oid: String): Future[Seq[WorkflowConfig]]     = firstSuccess(s"findWConfByOid(${oid})")(_.findWConfByOid(oid))
  def findWConfByXid(xid: String): Future[Option[WorkflowConfig]]  = firstSuccess(s"findWConfByXid(${xid})")(_.findWConfByXid(xid))

  // ---------------------------------------------------------------- WorkflowGraf
  def addGraf(g: WorkflowGraf): Future[WorkflowGraf]     = firstSuccess("addGraf")(_.addGraf(g))
  def getGraf(id: Int): Future[WorkflowGraf]             = firstSuccess(s"getGraf(${id})")(_.getGraf(id))
  def getGrafOpt(id: Int): Future[Option[WorkflowGraf]]  = firstSuccess(s"getGrafOpt(${id})")(_.getGrafOpt(id))
  def delGraf(id: Int): Future[Int]                      = firstSuccess(s"delGraf(${id})")(_.delGraf(id))
  def allGrafs: Future[Seq[WorkflowGraf]]                = firstSuccess("allGrafs")(_.allGrafs)
  def sizeGrafs: Future[Long]                            = firstSuccess("sizeGrafs")(_.sizeGrafs)

  // ---------------------------------------------------------------- DetectorSchema
  def addDSchema(dschema: DetectorSchema): Future[DetectorSchema]     = firstSuccess("addDSchema")(_.addDSchema(dschema))
  def getDSchema(id: Int): Future[Option[DetectorSchema]]       = firstSuccess(s"getDSchema(${id})")(_.getDSchema(id))
  def delDSchema(id: Int): Future[Int]                          = firstSuccess(s"delDSchema(${id})")(_.delDSchema(id))
  def allDSchemas: Future[Seq[DetectorSchema]]                  = firstSuccess("allDSchemas")(_.allDSchemas)
  def sizeDSchemas: Future[Long]                                = firstSuccess("sizeDSchemas")(_.sizeDSchemas)

  // ---------------------------------------------------------------- DetectorConfig
  def addDConf(dconf: DetectorConfig): Future[DetectorConfig]  = firstSuccess("addDConf")(_.addDConf(dconf))
  def getDConf(id: Int): Future[Option[DetectorConfig]]    = firstSuccess(s"getDConf(${id})")(_.getDConf(id))
  def delDConf(id: Int): Future[Int]                       = firstSuccess(s"delDConf(${id})")(_.delDConf(id))
  def allDConfs: Future[Seq[DetectorConfig]]               = firstSuccess("allDConfs")(_.allDConfs)
  def sizeDConfs: Future[Long]                             = firstSuccess("sizeDConfs")(_.sizeDConfs)
}
