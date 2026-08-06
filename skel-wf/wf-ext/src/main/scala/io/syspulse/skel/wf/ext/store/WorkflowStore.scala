package io.syspulse.skel.wf.ext.store

import scala.concurrent.{Future, ExecutionContext}

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf, WorkflowStatus}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract, DetectorConfigSchema, JsonSchemaDefault}

object WorkflowStore {
  val DETECTOR_CONFIG_SOURCE = "WORKFLOW" //"ext:workflow"

  // Paging result wrappers (mirror skel-user `Page`: items + total count).
  final case class PageSchema(schemas: Seq[WorkflowSchema], total: Long)
  final case class PageConfig(configs: Seq[WorkflowConfig], total: Long)
  final case class PageGraf(grafs: Seq[WorkflowGraf], total: Long)
  final case class PageDetectorSchema(schemas: Seq[DetectorSchema], total: Long)
  final case class PageDetectorConfig(configs: Seq[DetectorConfig], total: Long)

  /** In-memory slice: drop(from).take(size). */
  def page[T](xs: Seq[T], from: Long, size: Long): Seq[T] =
    xs.drop(from.max(0).toInt).take(size.max(0).toInt)

  /**
   * Instantiate a DetectorConfig from a DetectorSchema (a config "of" that schema), placed under the
   * given `contractId` (the detector's `contract.id` -> `detector.contract_id` FK). Default 0.
   */
  def detectorConfigOf(id: Int, ds: DetectorSchema, contractId: Int = 0): DetectorConfig = {
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
        0, 
        0, 
        None, 
        None, 
        None, 
        None, 
        ds.name),
      schema = Some(DetectorConfigSchema(
        ds.id, 
        now, 
        now, 
        ds.status, 
        ds.name, 
        ds.version, 
        None
      )),
      name = ds.name, 
      source = DETECTOR_CONFIG_SOURCE, 
      tags = Seq(), 
      config = ds.schema.map(JsonSchemaDefault.of), // instantiate default config from the JsonSchema spec
      destinations = Seq(),
    )
  }
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

  /**
   * Create a NEW WorkflowConfig from an existing WorkflowSchema, composed of DetectorConfig (NOT
   * DetectorSchema): for every graph node that references a DetectorSchema (`sid`), a DetectorConfig
   * is instantiated from that schema and linked as the node's `cid` (the `sid` is kept too).
   *
   * ALL ids (the WorkflowConfig and the new DetectorConfigs) are generated by THIS store - the caller
   * (Registry) never assigns them. Persists everything and returns the stored WorkflowConfig.
   */
  def createConfigFromSchema(schemaId: Int, contractId: Int = 0, name: Option[String] = None,
                             oid: Option[String] = None, pid: Option[String] = None, xid: Option[String] = None,
                             wid: Option[String] = None) // wid (if set) becomes the config title
                            (implicit ec: ExecutionContext): Future[WorkflowConfig] =
    getSchema(schemaId).flatMap { schema =>
      val nodes = schema.graph.nodes.values.toSeq.sortBy(_.id)
      nextDetectorConfigId.flatMap { dc0 =>
        // create one DetectorConfig per DetectorSchema-referencing node (sequential, distinct ids),
        // all placed under `contractId` (default 0 - see the `setup0` bootstrap)
        val cidByNodeF: Future[Map[Int, Int]] =
          nodes.foldLeft(Future.successful((dc0, Map.empty[Int, Int]))) { (accF, node) =>
            accF.flatMap { case (nextId, acc) =>
              if (node.sid < 0) Future.successful((nextId, acc))
              else getDetectorSchema(node.sid).flatMap {
                case Some(ds) => addDetectorConfig(WorkflowStore.detectorConfigOf(nextId, ds, contractId)).map(_ => (nextId + 1, acc + (node.id -> nextId)))
                case None     => Future.successful((nextId, acc)) // unresolved schema -> node keeps no cid
              }
            }
          }.map(_._2)
        for {
          cidByNode <- cidByNodeF
          wcId      <- nextConfigId
          grafId    <- nextGrafId
          cfgNodes   = schema.graph.nodes.map { case (k, n) => k -> n.copy(cid = cidByNode.get(n.id)) }
          cfgGraf    = WorkflowGraf.sync(schema.graph.copy(id = grafId, sid = Some(schema.id), cid = Some(wcId), nodes = cfgNodes))
          cfg        = WorkflowConfig.from(wcId, schema, name, oid, pid, xid, wid).copy(graph = cfgGraf)
          saved     <- addConfig(cfg)
          _         <- addGraf(cfgGraf)
        } yield saved
      }
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

  // ---------------------------------------------------------------- bootstrap
  // Ensure the default placement (tenant -> project -> contract) exists so DetectorConfigs created with
  // that contractId satisfy the external `detector.contract_id` FK. All ids/name/status are parameters
  // with sensible defaults. No-op for stores without those tables (Mem/Dir); WorkflowStoreDB inserts them.
  def setup0(tenantId: Int = 0, projectId: Int = 0, contractId: Int = 0,
             name: String = "setup0", status: String = "DISABLED")(implicit ec: ExecutionContext): Future[Unit] =
    Future.successful(())

  // ---------------------------------------------------------------- DetectorConfig
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
