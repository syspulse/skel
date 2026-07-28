package io.syspulse.skel.wf.ext.store

import scala.concurrent.{Future, ExecutionContext}

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}

object WorkflowStore {
  // Paging result wrappers (mirror skel-user `Page`: items + total count).
  final case class PageSchema(schemas: Seq[WorkflowSchema], total: Long)
  final case class PageConfig(configs: Seq[WorkflowConfig], total: Long)
  final case class PageGraf(grafs: Seq[WorkflowGraf], total: Long)
  final case class PageDetectorSchema(schemas: Seq[DetectorSchema], total: Long)
  final case class PageDetectorConfig(configs: Seq[DetectorConfig], total: Long)

  /** In-memory slice: drop(from).take(size). */
  def page[T](xs: Seq[T], from: Long, size: Long): Seq[T] =
    xs.drop(from.max(0).toInt).take(size.max(0).toInt)
}

/**
 * Single, unified, ASYNC (Future-based) datastore for all Workflow `ext` objects.
 *
 * It manages five object types in one store (per requirements - "Single WorkflowStore"):
 *   - WorkflowSchema   (template)
 *   - WorkflowConfig   (runtime instance)
 *   - WorkflowGraf     (visual graph; template or instance)
 *   - DetectorSchema   (referenced by graph nodes; needed for DSL id-lookups and ?detector=full)
 *   - DetectorConfig   (referenced by graph nodes; needed for DSL id-lookups and ?detector=full)
 *
 * Paging mirrors skel-user UserStore: `from`/`size` must both be set or both absent.
 */
trait WorkflowStore {

  // ---------------------------------------------------------------- WorkflowSchema
  def addSchema(s: WorkflowSchema): Future[WorkflowSchema]
  def getSchema(id: Int): Future[WorkflowSchema]
  def getSchemaOpt(id: Int): Future[Option[WorkflowSchema]]
  def delSchema(id: Int): Future[Int]
  def allSchemas: Future[Seq[WorkflowSchema]]
  def sizeSchemas: Future[Long]
  def listSchemas(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageSchema] =
    allSchemas.map { xs =>
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageSchema(items, xs.size.toLong)
    }

  // ---------------------------------------------------------------- WorkflowConfig
  def addConfig(c: WorkflowConfig): Future[WorkflowConfig]
  def getConfig(id: Int): Future[WorkflowConfig]
  def getConfigOpt(id: Int): Future[Option[WorkflowConfig]]
  def delConfig(id: Int): Future[Int]
  def allConfigs: Future[Seq[WorkflowConfig]]
  def sizeConfigs: Future[Long]
  def listConfigs(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageConfig] =
    allConfigs.map { xs =>
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageConfig(items, xs.size.toLong)
    }
  // owner can have many configs; xid points to a single runtime instance (unique)
  def findConfigByOid(oid: String): Future[Seq[WorkflowConfig]]
  def findConfigByXid(xid: String): Future[Option[WorkflowConfig]]

  // Update ONLY the status field of a WorkflowConfig (returns rows affected: 1 if updated, 0 if absent).
  // Default: read-modify-write; DB stores override with a targeted single-column UPDATE.
  def updateConfigStatus(id: Int, status: String)(implicit ec: ExecutionContext): Future[Int] =
    getConfigOpt(id).flatMap {
      case Some(c) => addConfig(c.copy(status = status, updatedAt = System.currentTimeMillis())).map(_ => 1)
      case None    => Future.successful(0)
    }

  // ---------------------------------------------------------------- WorkflowGraf
  def addGraf(g: WorkflowGraf): Future[WorkflowGraf]
  def getGraf(id: Int): Future[WorkflowGraf]
  def getGrafOpt(id: Int): Future[Option[WorkflowGraf]]
  def delGraf(id: Int): Future[Int]
  def allGrafs: Future[Seq[WorkflowGraf]]
  def sizeGrafs: Future[Long]
  def listGrafs(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageGraf] =
    allGrafs.map { xs =>
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageGraf(items, xs.size.toLong)
    }

  // ---------------------------------------------------------------- DetectorSchema
  def addDetectorSchema(d: DetectorSchema): Future[DetectorSchema]
  def getDetectorSchema(id: Int): Future[Option[DetectorSchema]]
  def delDetectorSchema(id: Int): Future[Int]
  def allDetectorSchemas: Future[Seq[DetectorSchema]]
  def sizeDetectorSchemas: Future[Long]
  def listDetectorSchemas(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageDetectorSchema] =
    allDetectorSchemas.map { xs =>
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageDetectorSchema(items, xs.size.toLong)
    }

  // ---------------------------------------------------------------- DetectorConfig
  // NOTE: WorkflowStoreDB does not implement full DetectorConfig writes yet (external `detector`
  // table) - its addDetectorConfig is a no-op that logs WARN. Mem/Dir persist normally.
  def addDetectorConfig(d: DetectorConfig): Future[DetectorConfig]
  def getDetectorConfig(id: Int): Future[Option[DetectorConfig]]

  // Update ONLY the status field of a DetectorConfig (returns rows affected: 1 if updated, 0 if absent).
  // Default: read-modify-write; DB stores override with a targeted single-column UPDATE (this IS
  // implemented for the external `detector` table, unlike the full addDetectorConfig write).
  def updateDetectorConfigStatus(id: Int, status: String)(implicit ec: ExecutionContext): Future[Int] =
    getDetectorConfig(id).flatMap {
      case Some(d) => addDetectorConfig(d.copy(status = status, updatedAt = System.currentTimeMillis())).map(_ => 1)
      case None    => Future.successful(0)
    }
  def delDetectorConfig(id: Int): Future[Int]
  def allDetectorConfigs: Future[Seq[DetectorConfig]]
  def sizeDetectorConfigs: Future[Long]
  def listDetectorConfigs(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageDetectorConfig] =
    allDetectorConfigs.map { xs =>
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageDetectorConfig(items, xs.size.toLong)
    }

  // ---------------------------------------------------------------- id generation
  // ids start at 0 and are never negative (max+1 within the entity collection)
  def nextSchemaId(implicit ec: ExecutionContext): Future[Int] =
    allSchemas.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextConfigId(implicit ec: ExecutionContext): Future[Int] =
    allConfigs.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextGrafId(implicit ec: ExecutionContext): Future[Int] =
    allGrafs.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextDetectorSchemaId(implicit ec: ExecutionContext): Future[Int] =
    allDetectorSchemas.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextDetectorConfigId(implicit ec: ExecutionContext): Future[Int] =
    allDetectorConfigs.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
}
