package io.syspulse.skel.wf.ext.store

import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.Logger

import io.hacken.ext.wf.{WorkflowConfig, WorkflowSchema}
import io.hacken.ext.detector.{DetectorConfig, DetectorSchema}

/**
 * Copy WorkflowStore entities from `src` (`--datastore`) to `dst` (`--datastore2`).
 *
 * Types: `all` | `WorkflowSchema` | `WorkflowConfig` | `DetectorSchema` | `DetectorConfig`.
 * Optional `id` copies one entity; omitted id copies every entity of that type.
 * `all` copies in FK-safe order: WorkflowSchema, DetectorSchema, DetectorConfig, WorkflowConfig.
 *
 * Per-entity failures are logged and skipped so the rest of the copy continues. Destination
 * `add*` is upsert-by-id (existing objects are updated, not inserted as duplicates).
 */
object WorkflowCopy {
  private val log = Logger(getClass)

  val TYPE_ALL     = "all"
  val TYPE_WSCHEMA = "WorkflowSchema"
  val TYPE_WCONF   = "WorkflowConfig"
  val TYPE_DSCHEMA = "DetectorSchema"
  val TYPE_DCONF   = "DetectorConfig"

  val Types: Seq[String] = Seq(TYPE_ALL, TYPE_WSCHEMA, TYPE_WCONF, TYPE_DSCHEMA, TYPE_DCONF)

  case class Error(typ: String, id: Int, cause: String) {
    def label: String = s"${typ}(${id}): ${cause}"
  }

  case class Result(copied: Seq[String] = Seq(), errors: Seq[Error] = Seq()) {
    def ++(other: Result): Result = Result(copied ++ other.copied, errors ++ other.errors)
    def summary: String = {
      val ok = if (copied.isEmpty) "copied: (none)" else s"copied (${copied.size}): ${copied.mkString(", ")}"
      val err =
        if (errors.isEmpty) "errors: (none)"
        else s"errors (${errors.size}): ${errors.map(_.label).mkString("; ")}"
      s"${ok}\n${err}"
    }
  }

  def parseType(s: String): Option[String] = {
    val n = s.trim
    Types.find(_.equalsIgnoreCase(n))
  }

  def apply(src: WorkflowStore, dst: WorkflowStore, typ: String, id: Option[Int])
           (implicit ec: ExecutionContext): Future[Result] =
    parseType(typ) match {
      case None =>
        Future.failed(new IllegalArgumentException(
          s"Unknown copy type: '${typ}'. Expected: ${Types.mkString("|")}"))
      case Some(TYPE_ALL) if id.isDefined =>
        Future.failed(new IllegalArgumentException("copy all does not take an id"))
      case Some(TYPE_ALL) =>
        copyAll(src, dst)
      case Some(t) =>
        copyType(src, dst, t, id)
    }

  private def copyAll(src: WorkflowStore, dst: WorkflowStore)
                     (implicit ec: ExecutionContext): Future[Result] =
    for {
      a <- copyType(src, dst, TYPE_WSCHEMA, None)
      b <- copyType(src, dst, TYPE_DSCHEMA, None)
      c <- copyType(src, dst, TYPE_DCONF, None)
      d <- copyType(src, dst, TYPE_WCONF, None)
    } yield a ++ b ++ c ++ d

  private def copyType(src: WorkflowStore, dst: WorkflowStore, typ: String, id: Option[Int])
                      (implicit ec: ExecutionContext): Future[Result] =
    typ match {
      case TYPE_WSCHEMA => copyWSchemas(src, dst, id)
      case TYPE_DSCHEMA => copyDSchemas(src, dst, id)
      case TYPE_DCONF   => copyDConfs(src, dst, id)
      case TYPE_WCONF   => copyWConfs(src, dst, id)
      case other        => Future.failed(new IllegalArgumentException(s"Unknown copy type: '${other}'"))
    }

  private def failed(typ: String, id: Int, e: Throwable): Result = {
    log.warn(s"copy ${typ}(${id}) failed: ${e.getMessage}")
    Result(errors = Seq(Error(typ, id, Option(e.getMessage).getOrElse(e.getClass.getName))))
  }

  private def missing(typ: String, id: Int): Result = {
    val cause = "not found"
    log.warn(s"copy ${typ}(${id}) failed: ${cause}")
    Result(errors = Seq(Error(typ, id, cause)))
  }

  private def tryPut(typ: String, id: Int, put: => Future[_])
                    (implicit ec: ExecutionContext): Future[Result] =
    put.map(_ => Result(copied = Seq(s"${typ}(${id})"))).recover { case e => failed(typ, id, e) }

  private def seqPut[T](xs: Seq[T], put: T => Future[_], typ: String, idOf: T => Int)
                       (implicit ec: ExecutionContext): Future[Result] =
    xs.foldLeft(Future.successful(Result())) { (acc, x) =>
      acc.flatMap(done => tryPut(typ, idOf(x), put(x)).map(done ++ _))
    }

  // Destination add* is upsert-by-id. Nested graf is best-effort: schema/config JSON already
  // carries the graph, so a graf-store failure does not fail the parent entity.
  private def putWSchema(dst: WorkflowStore, wschema: WorkflowSchema)
                        (implicit ec: ExecutionContext): Future[Unit] =
    dst.addWSchema(wschema).flatMap { _ =>
      dst.addGraf(wschema.graph).recover { case e =>
        log.warn(s"copy WorkflowGraf(${wschema.graph.id}) for ${TYPE_WSCHEMA}(${wschema.id}) failed: ${e.getMessage}")
        wschema.graph
      }.map(_ => ())
    }

  private def putWConf(dst: WorkflowStore, wconf: WorkflowConfig)
                      (implicit ec: ExecutionContext): Future[Unit] =
    dst.addWConf(wconf).flatMap { _ =>
      dst.addGraf(wconf.graph).recover { case e =>
        log.warn(s"copy WorkflowGraf(${wconf.graph.id}) for ${TYPE_WCONF}(${wconf.id}) failed: ${e.getMessage}")
        wconf.graph
      }.map(_ => ())
    }

  private def copyWSchemas(src: WorkflowStore, dst: WorkflowStore, id: Option[Int])
                          (implicit ec: ExecutionContext): Future[Result] =
    id match {
      case Some(i) =>
        src.getWSchemaOpt(i).flatMap {
          case Some(s) => tryPut(TYPE_WSCHEMA, s.id, putWSchema(dst, s))
          case None    => Future.successful(missing(TYPE_WSCHEMA, i))
        }
      case None =>
        src.allWSchemas
          .flatMap(xs => seqPut(xs.sortBy(_.id), (s: WorkflowSchema) => putWSchema(dst, s), TYPE_WSCHEMA, (s: WorkflowSchema) => s.id))
          .recover { case e => failed(TYPE_WSCHEMA, -1, e) }
    }

  private def copyDSchemas(src: WorkflowStore, dst: WorkflowStore, id: Option[Int])
                          (implicit ec: ExecutionContext): Future[Result] =
    id match {
      case Some(i) =>
        src.getDSchema(i).flatMap {
          case Some(s) => tryPut(TYPE_DSCHEMA, s.id, dst.addDSchema(s))
          case None    => Future.successful(missing(TYPE_DSCHEMA, i))
        }
      case None =>
        src.allDSchemas
          .flatMap(xs => seqPut(xs.sortBy(_.id), dst.addDSchema(_), TYPE_DSCHEMA, (s: DetectorSchema) => s.id))
          .recover { case e => failed(TYPE_DSCHEMA, -1, e) }
    }

  private def copyDConfs(src: WorkflowStore, dst: WorkflowStore, id: Option[Int])
                        (implicit ec: ExecutionContext): Future[Result] =
    id match {
      case Some(i) =>
        src.getDConf(i).flatMap {
          case Some(c) => tryPut(TYPE_DCONF, c.id, dst.addDConf(c))
          case None    => Future.successful(missing(TYPE_DCONF, i))
        }
      case None =>
        src.allDConfs
          .flatMap(xs => seqPut(xs.sortBy(_.id), dst.addDConf(_), TYPE_DCONF, (c: DetectorConfig) => c.id))
          .recover { case e => failed(TYPE_DCONF, -1, e) }
    }

  private def copyWConfs(src: WorkflowStore, dst: WorkflowStore, id: Option[Int])
                        (implicit ec: ExecutionContext): Future[Result] =
    id match {
      case Some(i) =>
        src.getWConfOpt(i).flatMap {
          case Some(c) => tryPut(TYPE_WCONF, c.id, putWConf(dst, c))
          case None    => Future.successful(missing(TYPE_WCONF, i))
        }
      case None =>
        src.allWConfs
          .flatMap(xs => seqPut(xs.sortBy(_.id), (c: WorkflowConfig) => putWConf(dst, c), TYPE_WCONF, (c: WorkflowConfig) => c.id))
          .recover { case e => failed(TYPE_WCONF, -1, e) }
    }
}
