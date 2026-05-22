package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.explain.Explain
import io.syspulse.skel.ErrNotFound

class ExplainStoreMem extends ExplainStore {
  private val log = Logger(getClass)

  var rules: Map[(Option[String], String), Explain] = Map()

  def +(r: Explain): Try[Explain] = {
    rules = rules + ((r.oid, r.rid) -> r)
    Success(r)
  }

  def del(oid: Option[String], rid: String): Try[Explain] =
    rules.get((oid, rid)) match {
      case Some(r) =>
        rules = rules - ((oid, rid))
        Success(r)
      case None =>
        Failure(new ErrNotFound(s"ExplainRule: $oid/$rid"))
    }

  def get(oid: Option[String], rid: String): Try[Explain] =
    rules.get((oid, rid)) match {
      case Some(r) => Success(r)
      case None    => Failure(new ErrNotFound(s"ExplainRule: $oid/$rid"))
    }

  def findByOid(oid: Option[String]): Seq[Explain] =
    rules.values.filter(_.oid == oid).toSeq

  def delByOid(oid: Option[String]): Try[Seq[Explain]] = {
    val deleted = rules.values.filter(_.oid == oid).toSeq
    deleted.foreach(r => rules = rules - ((r.oid, r.rid)))
    Success(deleted)
  }

  def all: Seq[Explain] = rules.values.toSeq

  def size: Long = rules.size
}
