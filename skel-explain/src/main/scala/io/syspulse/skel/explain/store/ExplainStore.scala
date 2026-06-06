package io.syspulse.skel.explain.store

import scala.concurrent.{Future, ExecutionContext}
import io.syspulse.skel.store.Store
import io.syspulse.skel.explain.Explain

object ExplainStore {
  val SEARCH_MIN_LEN = 3

  final case class Page(rules: Seq[Explain], total: Long)

  private val NonAlnum = "[^a-zA-Z0-9]+"
  private val CamelSplit = "([a-z])([A-Z])"

  /** "DetectorWallet" -> "Detector Wallet" so FTS can match "wallet" inside compound names. */
  def splitCamelCase(text: String): String =
    text.replaceAll(CamelSplit, "$1 $2")

  def normalizeSearchQuery(query: String): String = {
    val q = query.trim
    if (q.length >= 2 && ((q.head == '\'' && q.last == '\'') || (q.head == '"' && q.last == '"'))) q.substring(1, q.length - 1).trim
    else q
  }

  def tokenizeSearchField(text: String): Seq[String] =
    splitCamelCase(text).toLowerCase.replaceAll(NonAlnum, " ").split("\\s+").filter(_.nonEmpty)

  def postgresSearchTerms(query: String): Seq[String] =
    tokenizeSearchField(normalizeSearchQuery(query))

  def postgresPrefixTsQuery(query: String): Option[String] = {
    val terms = postgresSearchTerms(query)
    if (terms.isEmpty) None else Some(terms.map(t => s"$t:*").mkString(" & "))
  }
}

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

  def ???(from: Long, size: Long)(implicit ec: ExecutionContext): Future[Seq[Explain]] =
    list(from = Some(from), size = Some(size)).map(_.rules)

  def list(oid: Option[String] = None, rid: Option[String] = None, from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[ExplainStore.Page] =
    (from, size) match {
      case (Some(_), None) | (None, Some(_)) =>
        Future.failed(new IllegalArgumentException("from and size must both be set for paging"))
      case _ =>
        all.map { rules =>
          val filtered = filterRules(rules, oid, rid)
          val total = filtered.size.toLong
          val pageRules = (from, size) match {
            case (Some(f), Some(s)) => page(filtered, f, s)
            case _                    => filtered
          }
          ExplainStore.Page(pageRules, total)
        }
    }

  protected def filterRules(rules: Seq[Explain], oid: Option[String], rid: Option[String]): Seq[Explain] = {
    var filtered = rules
    oid.foreach(o => filtered = filtered.filter(_.oid == Option(o).filter(_.nonEmpty)))
    rid.foreach(r => filtered = filtered.filter(_.rid == r))
    filtered
  }

  def size: Future[Long]

  def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[ExplainStore.Page]

  protected def page(rules: Seq[Explain], from: Long, size: Long): Seq[Explain] =
    rules.drop(from.max(0).toInt).take(size.max(0).toInt)

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
