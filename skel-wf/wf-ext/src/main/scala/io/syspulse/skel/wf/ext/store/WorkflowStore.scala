package io.syspulse.skel.wf.ext.store

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf, WorkflowNode, WorkflowStatus}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract, DetectorConfigSchema, JsonSchemaDefault}
import io.syspulse.skel.util.UriUtil

object WorkflowStore {
  val DETECTOR_CONFIG_SOURCE = "WORKFLOW" //"ext:workflow"
  /** Sentinel for `add*`: store assigns the id (Mem/Dir: max+1; DB: `id_seq` RETURNING). */
  val NEW_ID = -1

  // Paging result wrappers (mirror skel-user `Page`: items + total count).
  final case class PageWSchema(wschemas: Seq[WorkflowSchema], total: Long)
  final case class PageWConf(wconfs: Seq[WorkflowConfig], total: Long)
  final case class PageGraf(grafs: Seq[WorkflowGraf], total: Long)
  final case class PageDSchema(dschemas: Seq[DetectorSchema], total: Long)
  final case class PageDConf(dconfs: Seq[DetectorConfig], total: Long)

  /** In-memory slice: drop(from).take(size). */
  def page[T](xs: Seq[T], from: Long, size: Long): Seq[T] =
    xs.drop(from.max(0).toInt).take(size.max(0).toInt)

  /**
   * Optional server-side filter/sort for WorkflowConfig listing (used by GET /config):
   *   - search: case-insensitive substring over name | title | xid
   *   - status: OR set-membership (empty = any)
   *   - tags:   AND / contains-all (empty = any)
   *   - tsStart/tsEnd: updatedAt range (epoch ms); API params ts0 (>=) / ts1 (<=)
   *   - sort:   "field:dir" (field = name|title|status|createdAt|updatedAt, dir = asc|desc; default updatedAt:desc)
   */
  final case class WConfFilter(
    search: Option[String] = None,
    status: Seq[String] = Seq(),
    tags: Seq[String] = Seq(),
    tsStart: Option[Long] = None,
    tsEnd: Option[Long] = None,
    sort: Option[String] = None,
  )
  val WConfFilterNone = WConfFilter()

  def sortWConfs(xs: Seq[WorkflowConfig], sort: Option[String]): Seq[WorkflowConfig] = {
    val (field, asc) = sort.map(_.split(":").toList match {
      case f :: dir :: _ => (f, dir.equalsIgnoreCase("asc"))
      case f :: Nil      => (f, false)
      case _             => ("updatedAt", false)
    }).getOrElse(("updatedAt", false))
    val ordered = field match {
      case "name"      => xs.sortBy(_.name.toLowerCase)
      case "title"     => xs.sortBy(_.title.toLowerCase)
      case "status"    => xs.sortBy(_.status.toLowerCase)
      case "createdAt" => xs.sortBy(_.createdAt)
      case _           => xs.sortBy(_.updatedAt)
    }
    if (asc) ordered else ordered.reverse
  }

  def filterSortWConfs(xs: Seq[WorkflowConfig], f: WConfFilter): Seq[WorkflowConfig] = {
    val searched = f.search.map(_.trim.toLowerCase).filter(_.nonEmpty) match {
      case Some(q) =>
        xs.filter(w =>
          w.name.toLowerCase.contains(q) ||
          w.title.toLowerCase.contains(q) ||
          w.xid.exists(_.toLowerCase.contains(q)))
      case None => xs
    }
    val statusSet = f.status.map(_.toUpperCase).filter(_.nonEmpty).toSet
    val byStatus =
      if (statusSet.isEmpty) searched
      else searched.filter(w => statusSet.contains(w.status.toUpperCase))
    val byTags =
      if (f.tags.isEmpty) byStatus
      else byStatus.filter(w => f.tags.forall(tg => w.tags.exists(_.equalsIgnoreCase(tg))))
    val byTime = byTags.filter(w =>
      f.tsStart.forall(w.updatedAt >= _) && f.tsEnd.forall(w.updatedAt <= _))
    sortWConfs(byTime, f.sort)
  }

  /**
   * Owner/project access filter for WorkflowConfig (string oid/pid fields).
   *   - `oid = None`  -> ignore owner (admin); `Some(o)` -> entity.oid must be Some(o)
   *   - `pid = None`  -> no project filter; `Some(p)` -> entity.pid must be Some(p)
   */
  def owned(entityOid: Option[String], entityPid: Option[String],
            oid: Option[String], pid: Option[String]): Boolean =
    oid.forall(o => entityOid.contains(o)) && pid.forall(p => entityPid.contains(p))

  /**
   * Owner/project access filter for DetectorConfig.
   * oid -> `contract.tenantId`, pid -> `contract.projectId` (numeric string ids).
   */
  def ownedDConf(dconf: DetectorConfig, oid: Option[String], pid: Option[String]): Boolean =
    oid.forall(o => o.toIntOption.contains(dconf.contract.tenantId)) &&
    pid.forall(p => p.toIntOption.contains(dconf.contract.projectId))

  /** Parse optional numeric oid/pid for DetectorConfigContract.tenantId / projectId. */
  def dconfTenantId(oid: Option[String]): Int = oid.flatMap(_.toIntOption).getOrElse(0)
  def dconfProjectId(pid: Option[String]): Int = pid.flatMap(_.toIntOption).getOrElse(0)

  /**
   * Instantiate a DetectorConfig from a DetectorSchema (a config "of" that schema), placed under the
   * given `contractId` (the detector's `contract.id` -> `detector.contract_id` FK). Default 0.
   * Optional oid/pid map to contract.tenantId / contract.projectId.
   */
  def dconfOf(id: Int, dschema: DetectorSchema, contractId: Int = 0,
              oid: Option[String] = None, pid: Option[String] = None): DetectorConfig = {
    val now = System.currentTimeMillis()
    DetectorConfig(
      id = id, 
      createdAt = now, 
      updatedAt = now,
      status = WorkflowStatus.UNKNOWN,
      contract = DetectorConfigContract(
        contractId, 
        now, 
        now, 
        dconfProjectId(pid),
        dconfTenantId(oid),
        None, 
        None, 
        None, 
        None, 
        dschema.name),
      schema = Some(DetectorConfigSchema(
        dschema.id, 
        now, 
        now, 
        dschema.status, 
        dschema.name, 
        dschema.version, 
        dschema.schema,
        dschema.uiSchema
      )),
      name = dschema.name, 
      source = DETECTOR_CONFIG_SOURCE, 
      tags = Seq(), 
      config = dschema.schema.map(JsonSchemaDefault.of), // instantiate default config from the JsonSchema spec
      destinations = Seq(),
    )
  }

  /** Sanitize icon URIs on every graph node (see [[UriUtil.uriSanitize]]). */
  def uriSanitize(g: WorkflowGraf): Try[WorkflowGraf] =
    g.nodes.foldLeft[Try[Map[Int, WorkflowNode]]](Success(Map.empty)) { case (acc, (id, n)) =>
      acc.flatMap { m =>
        UriUtil.uriSanitize(n.icon).map(ic => m + (id -> n.copy(icon = ic)))
      }
    }.map(nodes => g.copy(nodes = nodes))
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
  def addWSchema(wschema: WorkflowSchema): Future[WorkflowSchema]
  def getWSchema(id: Int): Future[WorkflowSchema]
  def getWSchemaOpt(id: Int): Future[Option[WorkflowSchema]]
  def delWSchema(id: Int): Future[Int]
  def allWSchemas: Future[Seq[WorkflowSchema]]
  def sizeWSchemas: Future[Long]
  def listWSchemas(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageWSchema] =
    allWSchemas.map { xs =>
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageWSchema(items, xs.size.toLong)
    }

  // ---------------------------------------------------------------- WorkflowConfig
  def addWConf(wconf: WorkflowConfig): Future[WorkflowConfig]
  def getWConf(id: Int): Future[WorkflowConfig]
  def getWConfOpt(id: Int): Future[Option[WorkflowConfig]]
  def delWConf(id: Int): Future[Int]
  def allWConfs: Future[Seq[WorkflowConfig]]
  def sizeWConfs: Future[Long]
  /**
   * List WorkflowConfigs filtered by optional `oid` / `pid`.
   * `oid = None` skips owner match (admin); `pid = None` skips project filter.
   */
  def listWConfs(from: Option[Long] = None, size: Option[Long] = None,
                 oid: Option[String] = None, pid: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageWConf] =
    allWConfs.map { all =>
      val xs = all.filter(w => WorkflowStore.owned(w.oid, w.pid, oid, pid))
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageWConf(items, xs.size.toLong)
    }
  /**
   * List WorkflowConfigs with optional server-side search/status/tags/time/sort filtering.
   * Store-agnostic: loads the owner scope (findWConfByOid when oid is set, else allWConfs),
   * applies owned(oid/pid) + filterSort, then pages. `total` is the filtered count.
   */
  def listWConfs(from: Option[Long], size: Option[Long], oid: Option[String], pid: Option[String],
                 filter: WorkflowStore.WConfFilter)(implicit ec: ExecutionContext): Future[WorkflowStore.PageWConf] = {
    val base: Future[Seq[WorkflowConfig]] = oid match {
      case Some(o) => findWConfByOid(o)
      case None    => allWConfs
    }
    base.map { xs =>
      val owned = xs.filter(w => WorkflowStore.owned(w.oid, w.pid, oid, pid))
      val filtered = WorkflowStore.filterSortWConfs(owned, filter)
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(filtered, f, s)
        case _                  => filtered
      }
      WorkflowStore.PageWConf(items, filtered.size.toLong)
    }
  }
  /** Get by id; fails with ErrNotFound when oid/pid filter does not match. */
  def getWConf(id: Int, oid: Option[String], pid: Option[String])(implicit ec: ExecutionContext): Future[WorkflowConfig] =
    getWConf(id).flatMap { wconf =>
      if (WorkflowStore.owned(wconf.oid, wconf.pid, oid, pid)) Future.successful(wconf)
      else Future.failed(new io.syspulse.skel.ErrNotFound(s"WorkflowConfig: ${id}"))
    }
  /** Delete by id only when oid/pid filter matches. */
  def delWConf(id: Int, oid: Option[String], pid: Option[String])(implicit ec: ExecutionContext): Future[Int] =
    getWConf(id, oid, pid).flatMap(_ => delWConf(id))
  // owner can have many configs; xid points to a single runtime instance (unique)
  def findWConfByOid(oid: String): Future[Seq[WorkflowConfig]]
  def findWConfByXid(xid: String): Future[Option[WorkflowConfig]]

  // Update ONLY the status field of a WorkflowConfig (returns rows affected: 1 if updated, 0 if absent).
  // Default: read-modify-write; DB stores override with a targeted single-column UPDATE.
  def updateWConfStatus(id: Int, status: String)(implicit ec: ExecutionContext): Future[Int] =
    getWConfOpt(id).flatMap {
      case Some(wconf) => addWConf(wconf.copy(status = status, updatedAt = System.currentTimeMillis())).map(_ => 1)
      case None        => Future.successful(0)
    }

  /**
   * Create a NEW WorkflowConfig from an existing WorkflowSchema, composed of DetectorConfig (NOT
   * DetectorSchema): for every graph node that references a DetectorSchema (`sid`), a DetectorConfig
   * is instantiated from that schema and linked as the node's `cid` (the `sid` is kept too).
   *
   * ALL ids (the WorkflowConfig and the new DetectorConfigs) come from `add*` return values —
   * the store assigns them (Mem/Dir via nextId, DB via `id_seq` RETURNING). The caller (Registry)
   * never assigns them. Persists everything and returns the stored WorkflowConfig.
   */
  def createWConfFromWSchema(wschemaId: Int, contractId: Int = 0, name: Option[String] = None,
                             oid: Option[String] = None, pid: Option[String] = None, xid: Option[String] = None,
                             wid: Option[String] = None, // wid (if set) becomes the config title when title is omitted
                             author: Option[String] = None,
                             title: Option[String] = None) // Name-field override of WorkflowSchema.title
                            (implicit ec: ExecutionContext): Future[WorkflowConfig] =
    getWSchema(wschemaId).flatMap { wschema =>
      val nodes = wschema.graph.nodes.values.toSeq.sortBy(_.id)
      // one DetectorConfig per DetectorSchema-referencing node. NEW_ID lets the store assign
      // the key (DB: id_seq RETURNING; Mem/Dir: max+1). Graph cids use the returned ids.
      val cidByNodeF: Future[Map[Int, Int]] =
        nodes.foldLeft(Future.successful(Map.empty[Int, Int])) { (accF, node) =>
          accF.flatMap { acc =>
            if (node.sid < 0) Future.successful(acc)
            else getDSchema(node.sid).flatMap {
              case Some(dschema) => addDConf(WorkflowStore.dconfOf(WorkflowStore.NEW_ID, dschema, contractId, oid, pid))
                .map(saved => acc + (node.id -> saved.id))
              case None          => Future.successful(acc)
            }
          }
        }
      for {
        cidByNode <- cidByNodeF
        cfgNodes   = wschema.graph.nodes.map { case (k, n) => k -> n.copy(cid = cidByNode.get(n.id)) }
        wconf0     = WorkflowConfig.from(WorkflowStore.NEW_ID, wschema, name, oid, pid, xid, wid, author, title)
                       .copy(graph = WorkflowGraf.sync(wschema.graph.copy(id = WorkflowStore.NEW_ID, sid = Some(wschema.id), cid = None, nodes = cfgNodes)))
        saved0    <- addWConf(wconf0)
        cfgGraf0   = WorkflowGraf.sync(wschema.graph.copy(id = WorkflowStore.NEW_ID, sid = Some(wschema.id), cid = Some(saved0.id), nodes = cfgNodes))
        savedGraf <- addGraf(cfgGraf0)
        wconf      = WorkflowConfig.from(saved0.id, wschema, name, oid, pid, xid, wid, author, title).copy(graph = savedGraf)
        saved     <- addWConf(wconf)
      } yield saved
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
  def addDSchema(dschema: DetectorSchema): Future[DetectorSchema]
  def getDSchema(id: Int): Future[Option[DetectorSchema]]
  def delDSchema(id: Int): Future[Int]
  def allDSchemas: Future[Seq[DetectorSchema]]
  def sizeDSchemas: Future[Long]
  def listDSchemas(from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageDSchema] =
    allDSchemas.map { xs =>
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageDSchema(items, xs.size.toLong)
    }

  // ---------------------------------------------------------------- bootstrap
  // Ensure the default placement (tenant -> project -> contract) exists so DetectorConfigs created with
  // that contractId satisfy the external `detector.contract_id` FK. All ids/name/status are parameters
  // with sensible defaults. No-op for stores without those tables (Mem/Dir); WorkflowStoreDB inserts them.
  def setup0(tenantId: Int = 0, projectId: Int = 0, contractId: Int = 0,
             name: String = "setup0", status: String = "DISABLED")(implicit ec: ExecutionContext): Future[Unit] =
    Future.successful(())

  // ---------------------------------------------------------------- DetectorConfig
  def addDConf(dconf: DetectorConfig): Future[DetectorConfig]
  def getDConf(id: Int): Future[Option[DetectorConfig]]

  // Update ONLY the status field of a DetectorConfig (returns rows affected: 1 if updated, 0 if absent).
  // Default: read-modify-write; DB stores override with a targeted single-column UPDATE (this IS
  // implemented for the external `detector` table, unlike the full addDConf write).
  def updateDConfStatus(id: Int, status: String)(implicit ec: ExecutionContext): Future[Int] =
    getDConf(id).flatMap {
      case Some(dconf) => addDConf(dconf.copy(status = status, updatedAt = System.currentTimeMillis())).map(_ => 1)
      case None        => Future.successful(0)
    }
  def delDConf(id: Int): Future[Int]
  def allDConfs: Future[Seq[DetectorConfig]]
  def sizeDConfs: Future[Long]
  /**
   * List DetectorConfigs filtered by optional `oid` / `pid`.
   * `oid = None` skips owner match (admin); `pid = None` skips project filter.
   */
  def listDConfs(from: Option[Long] = None, size: Option[Long] = None,
                 oid: Option[String] = None, pid: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageDConf] =
    allDConfs.map { all =>
      val xs = all.filter(d => WorkflowStore.ownedDConf(d, oid, pid))
      val items = (from, size) match {
        case (Some(f), Some(s)) => WorkflowStore.page(xs, f, s)
        case _                  => xs
      }
      WorkflowStore.PageDConf(items, xs.size.toLong)
    }
  /** Get by id; None when missing or oid/pid filter does not match (tenantId/projectId). */
  def getDConf(id: Int, oid: Option[String], pid: Option[String])(implicit ec: ExecutionContext): Future[Option[DetectorConfig]] =
    getDConf(id).map(_.filter(d => WorkflowStore.ownedDConf(d, oid, pid)))
  /** Delete by id only when oid/pid filter matches. */
  def delDConf(id: Int, oid: Option[String], pid: Option[String])(implicit ec: ExecutionContext): Future[Int] =
    getDConf(id, oid, pid).flatMap {
      case Some(_) => delDConf(id)
      case None    => Future.failed(new io.syspulse.skel.ErrNotFound(s"DetectorConfig: ${id}"))
    }

  // ---------------------------------------------------------------- id generation
  // ids start at 0 and are never negative (max+1 within the entity collection)
  def nextWSchemaId(implicit ec: ExecutionContext): Future[Int] =
    allWSchemas.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextWConfId(implicit ec: ExecutionContext): Future[Int] =
    allWConfs.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextGrafId(implicit ec: ExecutionContext): Future[Int] =
    allGrafs.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextDSchemaId(implicit ec: ExecutionContext): Future[Int] =
    allDSchemas.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
  def nextDConfId(implicit ec: ExecutionContext): Future[Int] =
    allDConfs.map(xs => if (xs.isEmpty) 0 else xs.map(_.id).max + 1)
}
