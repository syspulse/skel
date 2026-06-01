package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import io.syspulse.skel.store.Store
import io.syspulse.skel.explain.Explain

trait ExplainStore extends Store[Explain, String] {

  def oidKey(oid: Option[String]): String = oid.getOrElse("")

  // Composite key: oid_rid
  def getKey(r: Explain): String = s"${oidKey(r.oid)}_${r.rid}"

  def +(r: Explain): Future[Explain]

  def del(oid: Option[String], rid: String): Future[Explain]

  def get(oid: Option[String], rid: String): Future[Explain]

  def findByOid(oid: Option[String]): Future[Seq[Explain]]

  def delByOid(oid: Option[String]): Future[Seq[Explain]]

  def all: Future[Seq[Explain]]

  def size: Future[Long]

  def ?(key: String): Future[Explain] =
    key.split("_", 2).toList match {
      case oid :: rid :: Nil => get(Option(oid).filter(_.nonEmpty), rid)
      case _                 => Future.failed(new Exception(s"invalid key: '$key'"))
    }

  def del(key: String): Future[String] = {
    implicit val ec = scala.concurrent.ExecutionContext.global
    key.split("_", 2).toList match {
      case oid :: rid :: Nil => del(Option(oid).filter(_.nonEmpty), rid).map(_ => key)
      case _                 => Future.failed(new Exception(s"invalid key: '$key'"))
    }
  }
}
