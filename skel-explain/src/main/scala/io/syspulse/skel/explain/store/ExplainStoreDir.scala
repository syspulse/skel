package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.explain.ExplainRule
import io.syspulse.skel.explain.server.ExplainJson._

class ExplainStoreDir(dir: String = "store") extends StoreDir[ExplainRule, String](dir) with ExplainStore {
  override val log = Logger(getClass)

  val store = new ExplainStoreMem()

  override def getKey(r: ExplainRule): String = s"${r.oid}__${r.rid}"
  def toKey(id: String): String = id

  def all: Seq[ExplainRule] = store.all
  def size: Long = store.size

  override def +(r: ExplainRule): Try[ExplainRule] =
    super.+(r).flatMap(_ => store.+(r))

  override def del(key: String): Try[String] =
    store.del(key).map(_ => key).recoverWith { case _ =>
      super.del(key)
    }

  def del(oid: String, rid: String): Try[ExplainRule] =
    store.del(oid, rid) match {
      case Success(r) =>
        super.del(getKey(r))
        Success(r)
      case Failure(e) => Failure(e)
    }

  def get(oid: String, rid: String): Try[ExplainRule] = store.get(oid, rid)

  def findByOid(oid: String): Seq[ExplainRule] = store.findByOid(oid)

  override def ?(key: String): Try[ExplainRule] = store.?(key)

  load(dir)
}
