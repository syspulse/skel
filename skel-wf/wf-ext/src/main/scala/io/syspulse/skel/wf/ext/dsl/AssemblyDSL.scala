package io.syspulse.skel.wf.ext.dsl

import scala.util.Try
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import io.hacken.ext.wf._
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract, DetectorConfigSchema}
import io.syspulse.skel.wf.ext.store.WorkflowStore
import io.syspulse.skel.Err.NOT_FOUND
import io.syspulse.skel.ErrNotFound

// ============================================================================
// Assembly DSL
//
// Quickly build Workflows by linking Detector nodes into a DAG.
//
// Syntax per node:  {in}.{entity}.{name|id}.{out}
//   {in}     - optional input WorkflowLink.id (link id on the node, `cursor`). Omitted => first link.
//   {entity} - "Schema"  => create/reference a DetectorSchema only (node.cid stays None)
//              "Detector" => create/reference a DetectorConfig (and its DetectorSchema)
//   {name|id}- name => create new; numeric id => look up an existing entity in the datastore
//   {out}    - optional output WorkflowLink.id. Omitted => first link.
// Nodes are linked left-to-right with "->".
//
//   Detector.name1 -> Detector.name2.0 -> 1.Detector.name3.1
//
// builds: 3 DetectorSchema (Schema_name1/2/3), 3 DetectorConfig (name1/2/3),
//         1 WorkflowSchema, 1 WorkflowConfig, linked WorkflowNodes/WorkflowLinks.
// ============================================================================

case class NodeSpec(
  in: Option[Int],     // input link id
  entity: String,      // "Schema" | "Detector"
  ref: String,         // name or numeric id
  out: Option[Int],    // output link id
) {
  def isById: Boolean      = ref.nonEmpty && ref.forall(_.isDigit)
  def refId: Int           = ref.toInt
  def isDetector: Boolean  = entity.equalsIgnoreCase(AssemblyDSL.ENTITY_DETECTOR)
  def isSchemaOnly: Boolean = entity.equalsIgnoreCase(AssemblyDSL.ENTITY_SCHEMA)
}

case class AssemblyResult(
  schema: WorkflowSchema,
  config: Option[WorkflowConfig],
  detectorSchemas: Seq[DetectorSchema],   // newly created DetectorSchema objects
  detectorConfigs: Seq[DetectorConfig],   // newly created DetectorConfig objects
)

object AssemblyDSL {
  val log = Logger(getClass)

  val ENTITY_SCHEMA   = "Schema"
  val ENTITY_DETECTOR = "Detector"

  /**
   * Normalize a link-DSL pipeline. Accepts the bracket shorthand (`[PoO] -> [PoR] -> [Report]`)
   * and rewrites each bare/bracketed token into the Assembly DSL `Detector.<name>` form. Tokens
   * already carrying an entity keyword (`Detector.` / `Schema.`) are passed through unchanged.
   */
  def normalizePipeline(pipeline: String): String =
    pipeline.split("->").map(_.trim).filter(_.nonEmpty).map { tok0 =>
      val tok = tok0.stripPrefix("[").stripSuffix("]").trim
      val lower = tok.toLowerCase
      if (lower.contains("detector") || lower.contains("schema")) tok
      else s"${ENTITY_DETECTOR}.${tok}"
    }.mkString(" -> ")

  // ----------------------------------------------------------------- parsing (pure)
  def parse(pipeline: String): Seq[NodeSpec] =
    pipeline.split("->").map(_.trim).filter(_.nonEmpty).map(parseNode).toSeq

  def parseNode(spec: String): NodeSpec = {
    val parts = spec.split("\\.").map(_.trim).filter(_.nonEmpty)
    val idx = parts.indexWhere(p => p.equalsIgnoreCase(ENTITY_SCHEMA) || p.equalsIgnoreCase(ENTITY_DETECTOR))
    require(idx >= 0, s"node must specify entity ('${ENTITY_SCHEMA}' or '${ENTITY_DETECTOR}'): '${spec}'")
    val entity = parts(idx)
    val ref = parts.lift(idx + 1).getOrElse(
      throw new IllegalArgumentException(s"node must specify {name|id} after entity: '${spec}'"))
    val in  = if (idx > 0) Try(parts(idx - 1).toInt).toOption else None
    val out = parts.lift(idx + 2).flatMap(s => Try(s.toInt).toOption)
    NodeSpec(in, entity, ref, out)
  }

  private def detectorSchemaName(name: String): String = s"Schema_${name}"

  private def newDetectorSchema(id: Int, name: String): DetectorSchema = {
    val now = System.currentTimeMillis()
    DetectorSchema(
      id = id, createdAt = now, updatedAt = now, status = WorkflowSchema.Status.ACTIVE,
      name = detectorSchemaName(name), version = WorkflowSchema.Version.DEF_VERSION,
      title = detectorSchemaName(name), description = "", author = "",
      icon = None, faq = None, tags = Seq(), networkTags = Seq(),
      schema = None, uiSchema = None,
    )
  }

  private def newDetectorConfig(id: Int, name: String, ds: DetectorSchema): DetectorConfig = {
    val now = System.currentTimeMillis()
    DetectorConfig(
      id = id, createdAt = now, updatedAt = now, status = WorkflowStatus.ACTIVE,
      contract = DetectorConfigContract(
        id = 0, createdAt = now, updatedAt = now, projectId = 0, tenantId = 0,
        chainUid = None, proxyAddress = None, implementation = None, address = None, name = name),
      schema = Some(DetectorConfigSchema(
        id = ds.id, createdAt = now, updatedAt = now, status = WorkflowSchema.Status.ACTIVE,
        name = ds.name, version = ds.version, schema = ds.schema, uiSchema = ds.uiSchema)),
      name = name, source = "", tags = Seq(), config = None, destinations = Seq(),
    )
  }

  // ----------------------------------------------------------------- build (resolves against store, persists)

  /** Build & persist a WorkflowSchema (and any new DetectorSchema referenced by name). */
  def buildSchema(pipeline: String, store: WorkflowStore,
                  wid: Option[Int] = None, wname: Option[String] = None)
                 (implicit ec: ExecutionContext): Future[AssemblyResult] =
    build(pipeline, store, createConfig = false, wid, wname)

  /** Build & persist a WorkflowConfig with an underlying WorkflowSchema (and new Detector* by name). */
  def assembly(pipeline: String, store: WorkflowStore,
               wid: Option[Int] = None, wname: Option[String] = None)
              (implicit ec: ExecutionContext): Future[AssemblyResult] =
    build(pipeline, store, createConfig = true, wid, wname)

  private def randomName(): String = s"workflow-${java.util.UUID.randomUUID().toString.take(8)}"

  private def build(pipeline: String, store: WorkflowStore, createConfig: Boolean,
                    wid: Option[Int], wname: Option[String])
                   (implicit ec: ExecutionContext): Future[AssemblyResult] = {

    log.info(s"assemble: ${wid}/${wname}: ${pipeline}")

    val specs = parse(pipeline)
    require(specs.nonEmpty, s"empty assembly pipeline: '${pipeline}'")

    val asm = for {
      existingDS <- store.allDSchemas
      existingDC <- store.allDConfs
      ds0        <- store.nextDSchemaId
      dc0        <- store.nextDConfId
      wsId       <- wid.map(i => Future.successful(i.max(0))).getOrElse(store.nextWSchemaId)
      wcId       <- store.nextWConfId
      grafId     <- store.nextGrafId
      result     <- {
        val dsById = existingDS.map(d => d.id -> d).toMap
        val dcById = existingDC.map(d => d.id -> d).toMap

        var nextDs = ds0
        var nextDc = dc0
        val newDetectorSchemas = scala.collection.mutable.ListBuffer[DetectorSchema]()
        val newDetectorConfigs = scala.collection.mutable.ListBuffer[DetectorConfig]()

        // Within a single assembly, by-name nodes that share a name reuse ONE DetectorSchema
        // (created on first use) but each still gets its OWN DetectorConfig - i.e. several
        // DetectorConfigs of the same DetectorSchema with potentially different configurations.
        val schemaByName = scala.collection.mutable.Map[String, DetectorSchema]()
        def schemaForName(name: String): DetectorSchema =
          schemaByName.getOrElseUpdate(name, {
            val ds = newDetectorSchema(nextDs, name); nextDs += 1
            newDetectorSchemas += ds
            ds
          })

        // resolve each node spec -> (DetectorSchema id, optional DetectorConfig id)
        val resolved = specs.zipWithIndex.map { case (spec, i) =>
          if (spec.isById) {
            if (spec.isDetector) {
              val dc = dcById.getOrElse(spec.refId,
                throw new ErrNotFound(s"DetectorConfig not found: id=${spec.refId}"))
              val sid = dc.schema.map(_.id).getOrElse(-1)
              (i, spec, sid, Some(dc.id))
            } else {
              val ds = dsById.getOrElse(spec.refId,
                throw new ErrNotFound(s"DetectorSchema not found: id=${spec.refId}"))
              (i, spec, ds.id, None)
            }
          } else {
            // resolve (or create once) the DetectorSchema for this name. A new DetectorConfig is
            // created only when assembling a WorkflowConfig (`assembly`) for a `Detector` node; the
            // `schema` command (createConfig == false) creates DetectorSchema objects only.
            val ds = schemaForName(spec.ref)
            if (createConfig && spec.isDetector) {
              val dc = newDetectorConfig(nextDc, spec.ref, ds); nextDc += 1
              newDetectorConfigs += dc
              (i, spec, ds.id, Some(dc.id))
            } else (i, spec, ds.id, None)
          }
        }

        // persist new detectors, then build + persist the Workflow graph referencing them
        val persistDetectors =
          Future.sequence(newDetectorSchemas.toList.map(store.addDSchema)).flatMap { _ =>
            Future.sequence(newDetectorConfigs.toList.map(store.addDConf))
          }

        persistDetectors.flatMap { _ =>
          buildWorkflow(resolved, createConfig, wsId, wcId, grafId, wname, store).map {
            case (savedSchema, savedConfig) =>
              AssemblyResult(savedSchema, savedConfig, newDetectorSchemas.toList, newDetectorConfigs.toList)
          }
        }
      }
    } yield result

    asm.recover {
      case e =>
        log.error(s"assemble failed: ${e}",e)
        throw e
    }

    // log every created entity (raw toString) once the build completes
    asm.foreach(logCreated)
    asm
  }

  /** Log all entities created by an assembly as their raw toString (in creation order). */
  private def logCreated(r: AssemblyResult): Unit = {
    r.detectorSchemas.foreach(d => log.info(d.toString))
    r.detectorConfigs.foreach(d => log.info(d.toString))
    log.info(r.schema.toString)
    r.config.foreach(c => log.info(c.toString))
  }

  // ----------------------------------------------------------------- link (references existing detectors, creates nothing)

  /** Numeric version key ("1.2.3" -> Seq(1,2,3)) for "latest version" comparison; non-numeric parts dropped. */
  private def versionKey(v: String): Seq[Int] =
    Option(v).getOrElse("").split("[._-]").toSeq.flatMap(s => Try(s.toInt).toOption)

  /** Latest DetectorConfig among same-named candidates: highest schema.version, then updatedAt, then id. */
  private def latestConfig(cs: Seq[DetectorConfig]): Option[DetectorConfig] = {
    import scala.math.Ordering.Implicits._
    if (cs.isEmpty) None
    else Some(cs.sortBy(dc => (dc.schema.map(s => versionKey(s.version)).getOrElse(Seq.empty[Int]), dc.updatedAt, dc.id)).last)
  }

  /** Latest DetectorSchema among same-named candidates: highest version, then updatedAt, then id. */
  private def latestSchema(ds: Seq[DetectorSchema]): Option[DetectorSchema] = {
    import scala.math.Ordering.Implicits._
    if (ds.isEmpty) None
    else Some(ds.sortBy(d => (versionKey(d.version), d.updatedAt, d.id)).last)
  }

  /**
   * Build & persist a WorkflowConfig (+ WorkflowSchema) that REFERENCES existing Detector entities.
   * Unlike `assembly`, this NEVER creates DetectorConfig/DetectorSchema:
   *   - each `Detector.<name>` node is resolved to an existing DetectorConfig by name (latest version);
   *   - each `Schema.<name>`   node is resolved to an existing DetectorSchema by name (latest version);
   *   - `<id>` nodes are looked up by id.
   * The WorkflowSchema nodes reference each resolved DetectorConfig's DetectorSchema (via `schema.id`).
   * A missing reference fails the whole build.
   */
  def linkByName(pipeline: String, store: WorkflowStore,
                 wid: Option[Int] = None, wname: Option[String] = None)
                (implicit ec: ExecutionContext): Future[AssemblyResult] = {

    log.info(s"link: ${wid}/${wname}: ${pipeline}")

    val specs = parse(pipeline)
    require(specs.nonEmpty, s"empty link pipeline: '${pipeline}'")

    val asm = for {
      existingDC <- store.allDConfs
      existingDS <- store.allDSchemas
      wsId       <- wid.map(i => Future.successful(i.max(0))).getOrElse(store.nextWSchemaId)
      wcId       <- store.nextWConfId
      grafId     <- store.nextGrafId
      result     <- {
        val dcById   = existingDC.map(d => d.id -> d).toMap
        val dsById   = existingDS.map(d => d.id -> d).toMap
        val dcByName = existingDC.groupBy(_.name)
        val dsByName = existingDS.groupBy(_.name)

        // resolve each node spec against EXISTING detectors -> (index, spec, DetectorSchema id, optional DetectorConfig id)
        val resolved = specs.zipWithIndex.map { case (spec, i) =>
          if (spec.isSchemaOnly) {
            val ds =
              if (spec.isById) dsById.getOrElse(spec.refId,
                throw new ErrNotFound(s"DetectorSchema not found: id=${spec.refId}"))
              else latestSchema(dsByName.getOrElse(spec.ref, Seq())).getOrElse(
                throw new ErrNotFound(s"DetectorSchema not found: name='${spec.ref}'"))
            (i, spec, ds.id, Option.empty[Int])
          } else {
            val dc =
              if (spec.isById) dcById.getOrElse(spec.refId,
                throw new ErrNotFound(s"DetectorConfig not found: id=${spec.refId}"))
              else latestConfig(dcByName.getOrElse(spec.ref, Seq())).getOrElse(
                throw new ErrNotFound(s"DetectorConfig not found: name='${spec.ref}'"))
            (i, spec, dc.schema.map(_.id).getOrElse(-1), Some(dc.id))
          }
        }

        buildWorkflow(resolved, createConfig = true, wsId, wcId, grafId, wname, store).map {
          case (savedSchema, savedConfig) => AssemblyResult(savedSchema, savedConfig, Seq(), Seq())
        }
      }
    } yield result

    asm.recover { case e => log.error(s"link failed: ${e}", e); throw e }
    asm.foreach { r => log.info(r.schema.toString); r.config.foreach(c => log.info(c.toString)) }
    asm
  }

  // ----------------------------------------------------------------- shared graph build + persist
  // From resolved nodes (index, spec, DetectorSchema id, optional DetectorConfig id) build the
  // WorkflowSchema (+ WorkflowConfig when createConfig) with a left-to-right layout, and persist them
  // (+ their WorkflowGraf). Detector* entities are NOT created/persisted here.
  private def buildWorkflow(
      resolved: Seq[(Int, NodeSpec, Int, Option[Int])],
      createConfig: Boolean, wsId: Int, wcId: Int, grafId: Int, wname: Option[String],
      store: WorkflowStore)(implicit ec: ExecutionContext): Future[(WorkflowSchema, Option[WorkflowConfig])] = {

    // lay nodes out left-to-right so the assembled topology is visible in the UI editor
    // (otherwise all nodes default to pos 0,0 and overlap). Persisted in `meta`.
    def layoutMeta(i: Int): Map[String, Any] =
      WorkflowNode.defaultMeta ++ Map(
        "pos_x" -> (60 + i * 220),
        "pos_y" -> 120,
        "size_width" -> 160,
        "size_height" -> 64,
      )
    val schemaNodes = resolved.map { case (i, spec, sid, _) =>
      WorkflowNode(id = i, title = spec.ref, sid = sid, cid = None, meta = Some(layoutMeta(i)))
    }
    val configNodes = resolved.map { case (i, spec, sid, cid) =>
      WorkflowNode(id = i, title = spec.ref, sid = sid, cid = cid, meta = Some(layoutMeta(i)))
    }

    // link id prefers the upstream node's {out}, else the downstream node's {in}, else a free
    // sequential id. Collisions (e.g. an explicit id already used) fall back to the next free id.
    val usedLinkIds = scala.collection.mutable.Set[Int]()
    var seqLinkId = 0
    def freeId(): Int = { while (usedLinkIds.contains(seqLinkId)) seqLinkId += 1; seqLinkId }
    val links = (0 until resolved.size - 1).map { i =>
      val (_, sFrom, _, _) = resolved(i)
      val (_, sTo, _, _)   = resolved(i + 1)
      val candidate = sFrom.out.orElse(sTo.in).getOrElse(freeId())
      val linkId = if (usedLinkIds.contains(candidate)) freeId() else candidate
      usedLinkIds += linkId
      WorkflowLink(id = linkId, from = i, to = i + 1)
    }
    val linksMap = links.map(l => l.id -> l).toMap

    val schemaGraf = WorkflowGraf(
      id = grafId, sid = Some(wsId), cid = None,
      nodes = schemaNodes.map(n => n.id -> n).toMap,
      links = linksMap,
    )
    val schema = WorkflowSchema.of(wsId, wname.getOrElse(randomName()), WorkflowGraf.sync(schemaGraf))

    store.addWSchema(schema).flatMap { savedSchema =>
      if (!createConfig) {
        store.addGraf(WorkflowGraf.sync(schemaGraf)).map(_ => (savedSchema, None))
      } else {
        val configGraf = WorkflowGraf(
          id = grafId, sid = Some(wsId), cid = Some(wcId),
          nodes = configNodes.map(n => n.id -> n).toMap,
          links = linksMap,
        )
        val config = WorkflowConfig.from(wcId, savedSchema)
          .copy(graph = WorkflowGraf.sync(configGraf))
        for {
          savedConfig <- store.addWConf(config)
          _           <- store.addGraf(WorkflowGraf.sync(configGraf))
        } yield (savedSchema, Some(savedConfig))
      }
    }
  }
}
