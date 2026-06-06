package io.syspulse.skel.explain.store

import java.util.regex.Pattern

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

  override def list(oid: Option[String] = None, rid: Option[String] = None, from: Option[Long] = None, size: Option[Long] = None)(implicit ec: scala.concurrent.ExecutionContext): Future[ExplainStore.Page] =
    super.list(oid, rid, from, size)

  private def regexpMatch(pattern: Pattern, text: String): Boolean =
    pattern.matcher(text).find()

  def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[ExplainStore.Page] = {
    val q = query

    if (q.length < ExplainStore.SEARCH_MIN_LEN) return Future.successful(ExplainStore.Page(Seq.empty, 0))

    val pattern =
      try Pattern.compile(q, Pattern.CASE_INSENSITIVE)
      catch { case _: Exception => return Future.failed(new Exception(s"invalid regex: '${q}'")) }

    val matched = rules.values.filter { r =>
      r.name.exists(regexpMatch(pattern, _)) ||
        r.desc.exists(regexpMatch(pattern, _))
    }.toSeq

    val total = matched.size.toLong
    val pageRules = (from, size) match {
      case (Some(f), Some(s)) => page(matched, f, s)
      case (None, None)       => matched
      case _                  => matched
    }
    Future.successful(ExplainStore.Page(pageRules, total))
  }
}
