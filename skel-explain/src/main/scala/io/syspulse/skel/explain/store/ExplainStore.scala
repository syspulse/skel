package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import io.syspulse.skel.store.Store
import io.syspulse.skel.explain.Explain

trait ExplainStore extends Store[Explain, String] {

  def oidKey(oid: Option[String]): String = oid.getOrElse("")

  // Composite key: oid_rid
  def getKey(r: Explain): String = s"${oidKey(r.oid)}_${r.rid}"

  def +(r: Explain): Try[Explain]

  def del(oid: Option[String], rid: String): Try[Explain]

  def get(oid: Option[String], rid: String): Try[Explain]

  def findByOid(oid: Option[String]): Seq[Explain]

  def delByOid(oid: Option[String]): Try[Seq[Explain]]

  def all: Seq[Explain]

  def size: Long

  def ?(key: String): Try[Explain] =
    key.split("_", 2).toList match {
      case oid :: rid :: Nil => get(Option(oid).filter(_.nonEmpty), rid)
      case _                 => Failure(new Exception(s"invalid key: '$key'"))
    }

  def del(key: String): Try[String] =
    key.split("_", 2).toList match {
      case oid :: rid :: Nil => del(Option(oid).filter(_.nonEmpty), rid).map(_ => key)
      case _                 => Failure(new Exception(s"invalid key: '$key'"))
    }
}
