package io.syspulse.skel.wf.ext.store

import scala.concurrent.Future
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.ErrNotFound
import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}

class WorkflowStoreMem extends WorkflowStore {
  protected val log = Logger(getClass)

  var schemas: Map[Int, WorkflowSchema] = Map()
  var configs: Map[Int, WorkflowConfig] = Map()
  var grafs:   Map[Int, WorkflowGraf]   = Map()
  var dSchemas: Map[Int, DetectorSchema] = Map()
  var dConfigs: Map[Int, DetectorConfig] = Map()

  // ---------------------------------------------------------------- WorkflowSchema
  def addSchema(s: WorkflowSchema): Future[WorkflowSchema] = { schemas = schemas + (s.id -> s); Future.successful(s) }
  def getSchema(id: Int): Future[WorkflowSchema] = schemas.get(id) match {
    case Some(s) => Future.successful(s)
    case None    => Future.failed(new ErrNotFound(s"WorkflowSchema: ${id}"))
  }
  def getSchemaOpt(id: Int): Future[Option[WorkflowSchema]] = Future.successful(schemas.get(id))
  def delSchema(id: Int): Future[Int] = {
    if (!schemas.contains(id)) Future.failed(new ErrNotFound(s"WorkflowSchema: ${id}"))
    else { schemas = schemas - id; Future.successful(id) }
  }
  def allSchemas: Future[Seq[WorkflowSchema]] = Future.successful(schemas.values.toSeq)
  def sizeSchemas: Future[Long] = Future.successful(schemas.size.toLong)

  // ---------------------------------------------------------------- WorkflowConfig
  def addConfig(c: WorkflowConfig): Future[WorkflowConfig] = { configs = configs + (c.id -> c); Future.successful(c) }
  def getConfig(id: Int): Future[WorkflowConfig] = configs.get(id) match {
    case Some(c) => Future.successful(c)
    case None    => Future.failed(new ErrNotFound(s"WorkflowConfig: ${id}"))
  }
  def getConfigOpt(id: Int): Future[Option[WorkflowConfig]] = Future.successful(configs.get(id))
  def delConfig(id: Int): Future[Int] = {
    if (!configs.contains(id)) Future.failed(new ErrNotFound(s"WorkflowConfig: ${id}"))
    else { configs = configs - id; Future.successful(id) }
  }
  def allConfigs: Future[Seq[WorkflowConfig]] = Future.successful(configs.values.toSeq)
  def sizeConfigs: Future[Long] = Future.successful(configs.size.toLong)
  def findConfigByOid(oid: String): Future[Seq[WorkflowConfig]] =
    Future.successful(configs.values.filter(_.oid.contains(oid)).toSeq)
  def findConfigByXid(xid: String): Future[Option[WorkflowConfig]] =
    Future.successful(configs.values.find(_.xid.exists(_.equalsIgnoreCase(xid))))

  // ---------------------------------------------------------------- WorkflowGraf
  def addGraf(g: WorkflowGraf): Future[WorkflowGraf] = {
    val g1 = WorkflowGraf.sync(g) // keep node.links in sync with graf.links
    grafs = grafs + (g1.id -> g1); Future.successful(g1)
  }
  def getGraf(id: Int): Future[WorkflowGraf] = grafs.get(id) match {
    case Some(g) => Future.successful(g)
    case None    => Future.failed(new ErrNotFound(s"WorkflowGraf: ${id}"))
  }
  def getGrafOpt(id: Int): Future[Option[WorkflowGraf]] = Future.successful(grafs.get(id))
  def delGraf(id: Int): Future[Int] = {
    if (!grafs.contains(id)) Future.failed(new ErrNotFound(s"WorkflowGraf: ${id}"))
    else { grafs = grafs - id; Future.successful(id) }
  }
  def allGrafs: Future[Seq[WorkflowGraf]] = Future.successful(grafs.values.toSeq)
  def sizeGrafs: Future[Long] = Future.successful(grafs.size.toLong)

  // ---------------------------------------------------------------- DetectorSchema
  def addDetectorSchema(d: DetectorSchema): Future[DetectorSchema] = { dSchemas = dSchemas + (d.id -> d); Future.successful(d) }
  def getDetectorSchema(id: Int): Future[Option[DetectorSchema]] = Future.successful(dSchemas.get(id))
  def delDetectorSchema(id: Int): Future[Int] = {
    if (!dSchemas.contains(id)) Future.failed(new ErrNotFound(s"DetectorSchema: ${id}"))
    else { dSchemas = dSchemas - id; Future.successful(id) }
  }
  def allDetectorSchemas: Future[Seq[DetectorSchema]] = Future.successful(dSchemas.values.toSeq)
  def sizeDetectorSchemas: Future[Long] = Future.successful(dSchemas.size.toLong)

  // ---------------------------------------------------------------- DetectorConfig
  def addDetectorConfig(d: DetectorConfig): Future[DetectorConfig] = { dConfigs = dConfigs + (d.id -> d); Future.successful(d) }
  def getDetectorConfig(id: Int): Future[Option[DetectorConfig]] = Future.successful(dConfigs.get(id))
  def delDetectorConfig(id: Int): Future[Int] = {
    if (!dConfigs.contains(id)) Future.failed(new ErrNotFound(s"DetectorConfig: ${id}"))
    else { dConfigs = dConfigs - id; Future.successful(id) }
  }
  def allDetectorConfigs: Future[Seq[DetectorConfig]] = Future.successful(dConfigs.values.toSeq)
  def sizeDetectorConfigs: Future[Long] = Future.successful(dConfigs.size.toLong)
}
