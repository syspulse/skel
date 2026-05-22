package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.explain.Explain
import io.syspulse.skel.explain.server.ExplainJson._

class ExplainStoreDir(dir: String = "store") extends StoreDir[Explain, String](dir) with ExplainStore {
  override val log = Logger(getClass)

  val store = new ExplainStoreMem()

  def toKey(id: String): String = id

  def all: Seq[Explain] = store.all
  def size: Long = store.size

  override def +(r: Explain): Try[Explain] =
    super.+(r).flatMap(_ => store.+(r))

  override def del(key: String): Try[String] =
    store.del(key).map(_ => key).recoverWith { case _ =>
      super.del(key)
    }

  def del(oid: Option[String], rid: String): Try[Explain] =
    store.del(oid, rid) match {
      case Success(r) =>
        super[StoreDir].del(getKey(r))
        Success(r)
      case Failure(e) => Failure(e)
    }

  def get(oid: Option[String], rid: String): Try[Explain] = store.get(oid, rid)

  def findByOid(oid: Option[String]): Seq[Explain] = store.findByOid(oid)

  def delByOid(oid: Option[String]): Try[Seq[Explain]] = {
    val deleted = store.findByOid(oid)
    deleted.foreach(r => super[StoreDir].del(getKey(r)))
    store.delByOid(oid).map(_ => deleted)
  }

  override def ?(key: String): Try[Explain] = store.?(key)

  load(dir)
}
