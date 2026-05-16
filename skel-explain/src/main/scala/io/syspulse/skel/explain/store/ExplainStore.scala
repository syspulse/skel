package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import io.syspulse.skel.store.Store
import io.syspulse.skel.explain.ExplainRule

trait ExplainStore extends Store[ExplainRule, String] {

  // Composite key: oid__rid
  def getKey(r: ExplainRule): String = s"${r.oid}__${r.rid}"

  def +(r: ExplainRule): Try[ExplainRule]

  def del(oid: String, rid: String): Try[ExplainRule]

  def get(oid: String, rid: String): Try[ExplainRule]

  def findByOid(oid: String): Seq[ExplainRule]

  def all: Seq[ExplainRule]

  def size: Long

  def ?(key: String): Try[ExplainRule] =
    key.split("__", 2).toList match {
      case oid :: rid :: Nil => get(oid, rid)
      case _                 => Failure(new Exception(s"invalid key: '$key'"))
    }

  def del(key: String): Try[String] =
    key.split("__", 2).toList match {
      case oid :: rid :: Nil => del(oid, rid).map(_ => key)
      case _                 => Failure(new Exception(s"invalid key: '$key'"))
    }
}
