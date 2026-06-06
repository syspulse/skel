package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.explain.Explain
import io.syspulse.skel.explain.server.ExplainJson._

class ExplainStoreDir(dir: String = "store") extends StoreDir[Explain, String](dir) with ExplainStore {
  override val log = Logger(getClass)

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val store = new ExplainStoreMem()

  def toKey(id: String): String = id

  def all: Future[Seq[Explain]] = store.all
  override def list(oid: Option[String] = None, rid: Option[String] = None, from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[ExplainStore.Page] =
    store.list(oid, rid, from, size)
  override def ???(from: Long, size: Long)(implicit ec: ExecutionContext): Future[Seq[Explain]] =
    store.???(from, size)
  def size: Future[Long] = store.size

  override def +(r: Explain): Future[Explain] =
    super.+(r).flatMap(_ => store.+(r))

  override def del(key: String): Future[String] =
    store.del(key).recoverWith { case _ => super.del(key) }

  def del(oid: Option[String], rid: String): Future[Explain] =
    store.del(oid, rid).flatMap { r =>
      super[StoreDir].del(getKey(r)).map(_ => r)
    }

  def get(oid: Option[String], rid: String): Future[Explain] = store.get(oid, rid)

  def findByOid(oid: Option[String]): Future[Seq[Explain]] = store.findByOid(oid)

  override def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[ExplainStore.Page] =
    store.search(query, from, size)

  def delByOid(oid: Option[String]): Future[Seq[Explain]] = {
    store.findByOid(oid).flatMap { deleted =>
      val futs = deleted.map(r => super[StoreDir].del(getKey(r)))
      Future.sequence(futs).flatMap(_ => store.delByOid(oid))
    }
  }

  override def ?(key: String): Future[Explain] = store.?(key)

  load(dir)
}
