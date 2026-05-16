package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.explain.ExplainRule
import io.syspulse.skel.ErrNotFound

class ExplainStoreMem extends ExplainStore {
  private val log = Logger(getClass)

  var rules: Map[(String, String), ExplainRule] = Map()

  def +(r: ExplainRule): Try[ExplainRule] = {
    rules = rules + ((r.oid, r.rid) -> r)
    Success(r)
  }

  def del(oid: String, rid: String): Try[ExplainRule] =
    rules.get((oid, rid)) match {
      case Some(r) =>
        rules = rules - ((oid, rid))
        Success(r)
      case None =>
        Failure(new ErrNotFound(s"ExplainRule: $oid/$rid"))
    }

  def get(oid: String, rid: String): Try[ExplainRule] =
    rules.get((oid, rid)) match {
      case Some(r) => Success(r)
      case None    => Failure(new ErrNotFound(s"ExplainRule: $oid/$rid"))
    }

  def findByOid(oid: String): Seq[ExplainRule] =
    rules.values.filter(_.oid == oid).toSeq

  def all: Seq[ExplainRule] = rules.values.toSeq

  def size: Long = rules.size
}
