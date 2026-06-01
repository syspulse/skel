package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.explain.Explain
import io.syspulse.skel.ErrNotFound

class ExplainStoreMem extends ExplainStore {
  private val log = Logger(getClass)

  var rules: Map[(Option[String], String), Explain] = Map()

  def +(r: Explain): Future[Explain] = {
    rules = rules + ((r.oid, r.rid) -> r)
    Future.successful(r)
  }

  def del(oid: Option[String], rid: String): Future[Explain] =
    rules.get((oid, rid)) match {
      case Some(r) =>
        rules = rules - ((oid, rid))
        Future.successful(r)
      case None =>
        Future.failed(new ErrNotFound(s"ExplainRule: $oid/$rid"))
    }

  def get(oid: Option[String], rid: String): Future[Explain] =
    rules.get((oid, rid)) match {
      case Some(r) => Future.successful(r)
      case None    => Future.failed(new ErrNotFound(s"ExplainRule: $oid/$rid"))
    }

  def findByOid(oid: Option[String]): Future[Seq[Explain]] =
    Future.successful(rules.values.filter(_.oid == oid).toSeq)

  def delByOid(oid: Option[String]): Future[Seq[Explain]] = {
    val deleted = rules.values.filter(_.oid == oid).toSeq
    deleted.foreach(r => rules = rules - ((r.oid, r.rid)))
    Future.successful(deleted)
  }

  def all: Future[Seq[Explain]] = Future.successful(rules.values.toSeq)

  def size: Future[Long] = Future.successful(rules.size.toLong)
}
