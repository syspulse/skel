package io.syspulse.skel.wf.ext.event

import java.time.{Instant, ZoneOffset}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.Logger
import com.sksamuel.elastic4s.{ElasticClient, Response}
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.queries.Query

import io.syspulse.skel.uri.ElasticURI

/**
 * Async OpenSearch store (elastic4s). All operations return `Future` and never block on `.await`.
 *
 * Reads use `index` (Dev: search alias `detector-alert-search` over yearly `detector-alert-YYYY`).
 * Writes use `_id = {deid}:{eid}`. A search alias with no write index is not writable; those
 * upserts go to `{prefix}-{year}` from `ts` (UTC), matching existing detector-alert documents.
 */
class EventStoreElastic(val client: ElasticClient, index0: String)(implicit ec: ExecutionContext) extends EventStore {
  private val log = Logger(this.getClass)
  val index: String = Option(index0).map(_.trim).filter(_.nonEmpty).getOrElse(EventStore.DEF_INDEX)
  @volatile private var writeYearly: Boolean = false

  import ElasticDsl._

  /** Log and keep the elastic4s exception (client transport or `ElasticError.asException`). */
  private def run[T](op: String)(fut: Future[Response[T]]): Future[T] =
    fut.transform {
      case Failure(e) =>
        //log.error(s"OpenSearch ${op} failed: ${e.getMessage}", e)
        Failure(e)
      case Success(resp) if resp.isError =>
        val e = resp.error.asException
        //log.warn(s"OpenSearch ${op} failed: ${e.getMessage}", e)
        Failure(e)
      case Success(resp) =>
        Success(resp.result)
    }

  private def execute[T](op: String)(fut: Future[Response[T]]): Future[Response[T]] =
    fut.transform {
      case Failure(e) =>
        //log.error(s"OpenSearch ${op} failed: ${e.getMessage}", e)
        Failure(e)
      case s => s
    }

  private def missingIndex(reason: String): Boolean =
    reason.contains("index_not_found") || reason.contains("no such index")

  private def aliasBlocksSingleIndexOp(reason: String): Boolean = {
    val r = reason.toLowerCase
    r.contains("no write index") || r.contains("more than one index") || r.contains("alias")
  }

  private def bulkUpsert(indexName: String, batch: Seq[Alert]): Future[Seq[Alert]] = {
    val reqs = batch.map { a =>
      indexInto(indexName)
        .id(a.id)
        .source(Alert.sourceJson(a).compactPrint)
        .refresh(RefreshPolicy.WaitFor)
    }
    run("bulk upsert")(client.execute(bulk(reqs).refresh(RefreshPolicy.WaitFor))).flatMap { br =>
      if (!br.hasFailures) Future.successful(batch)
      else {
        val reasons = br.failures.map(i => i.error.map(_.reason).getOrElse(s"id=${i.id} status=${i.status}"))
        Future.failed(new RuntimeException(s"OpenSearch bulk upsert failed index=${indexName}: ${reasons.mkString("; ")}"))
      }
    }
  }

  def upsert(alerts: Seq[Alert]): Future[Seq[Alert]] = {
    if (alerts.isEmpty) return Future.successful(Seq.empty)

    def writeAll(yearly: Boolean): Future[Seq[Alert]] = {
      val groups =
        if (!yearly) Seq(index -> alerts)
        else alerts.groupBy(a => EventStoreElastic.yearlyIndex(index, a.ts)).toSeq
      Future.sequence(groups.map { case (idx, batch) => bulkUpsert(idx, batch) }).map(_ => alerts)
    }

    writeAll(writeYearly).recoverWith {
      case e if !writeYearly && aliasBlocksSingleIndexOp(Option(e.getMessage).getOrElse("")) =>
        writeYearly = true
        val sample = EventStoreElastic.yearlyIndex(index, alerts.head.ts)
        log.warn(s"OpenSearch ${index} is not writable; writing to yearly indices (e.g. ${sample})")
        writeAll(true)
    }
  }

  def getById(id: String): Future[Option[Alert]] = {
    execute("get")(client.execute(ElasticDsl.get(index, id))).flatMap { resp =>
      if (resp.isError) {
        val reason = Try(resp.error.reason).getOrElse("")
        if (missingIndex(reason)) Future.successful(None)
        else if (aliasBlocksSingleIndexOp(reason)) getByIdSearch(id)
        else {
          val e = resp.error.asException
          //log.error(s"OpenSearch get failed: ${e.getMessage}", e)
          Future.failed(e)
        }
      } else {
        val r = resp.result
        if (!r.found) Future.successful(None)
        else Future.successful(Some(fromHit(r.id, r.sourceAsMap, r.sourceAsString)))
      }
    }
  }

  private def getByIdSearch(id: String): Future[Option[Alert]] =
    run("getById")(client.execute(ElasticDsl.search(index).query(idsQuery(id)).size(1))).map { r =>
      r.hits.hits.headOption.map(h => fromHit(h.id, h.sourceAsMap, h.sourceAsString))
    }

  def getByEid(eid: String, oid: Option[Long] = None): Future[Seq[Alert]] = {
    val filters = scala.collection.mutable.ListBuffer[Query](termQuery("eid", eid))
    oid.foreach(v => filters += termQuery("teid", v))
    search(filters.toList, from = 0, size = 1000).map(_.alerts)
  }

  def query(q: EventQuery): Future[EventPage] = {
    val filters = scala.collection.mutable.ListBuffer[Query]()
    if (q.ts0.isDefined || q.ts1.isDefined) {
      var rq = rangeQuery("ts")
      q.ts0.foreach(v => rq = rq.gte(v))
      q.ts1.foreach(v => rq = rq.lte(v))
      filters += rq
    }
    q.oid.foreach(v => filters += termQuery("teid", v))
    q.pid.foreach(v => filters += termQuery("prid", v))
    q.did.foreach(v => filters += termQuery("deid", v))
    q.cid.foreach(v => filters += termQuery("coid", v))
    q.sid.foreach(v => filters += termQuery("sid", v))
    search(filters.toList, q.from.getOrElse(0L).toInt.max(0), q.size.getOrElse(10L).toInt.max(0))
  }

  private def search(filters: List[Query], from: Int, size: Int): Future[EventPage] = {
    val sreq = ElasticDsl.search(index)
      .query(if (filters.isEmpty) matchAllQuery() else boolQuery().filter(filters))
      .from(from)
      .size(size)
      .sortByFieldDesc("ts")
      .trackTotalHits(true)

    run("search")(client.execute(sreq)).map { r =>
      val alerts = r.hits.hits.toSeq.map { h =>
        fromHit(h.id, h.sourceAsMap, h.sourceAsString)
      }
      EventPage(alerts, r.totalHits)
    }
  }

  def delById(id: String): Future[Boolean] = {
    execute("delete")(client.execute(deleteById(index, id).refresh(RefreshPolicy.WaitFor))).flatMap { resp =>
      if (resp.isError) {
        val reason = Try(resp.error.reason).getOrElse("")
        if (missingIndex(reason) || reason.contains("404")) Future.successful(false)
        else if (aliasBlocksSingleIndexOp(reason)) delByIdSearch(id)
        else {
          val e = resp.error.asException
          Future.failed(e)
        }
      } else Future.successful(resp.result.result == "deleted")
    }
  }

  private def delByIdSearch(id: String): Future[Boolean] =
    run("deleteById")(
      client.execute(deleteByQuery(index, idsQuery(id)).refreshImmediately.waitForCompletion(true))
    ).map {
      case Left(r)  => r.deleted > 0
      case Right(_) => false
    }

  def delByEid(eid: String, oid: Option[Long] = None): Future[Int] = {
    val filters = scala.collection.mutable.ListBuffer[Query](termQuery("eid", eid))
    oid.foreach(v => filters += termQuery("teid", v))
    run("deleteByQuery")(
      client.execute(deleteByQuery(index, boolQuery().filter(filters.toList)).refreshImmediately.waitForCompletion(true))
    ).map {
      case Left(r)  => r.deleted.toInt
      case Right(_) => 0
    }
  }

  /** Create the index with detector-alert-search compatible mappings. Idempotent. Not used on Dev. */
  def ensureIndex(): Future[Unit] = {
    val mapping =
      """{
        |  "mappings": {
        |    "properties": {
        |      "ts":   { "type": "date" },
        |      "eid":  { "type": "keyword" },
        |      "tx":   { "type": "keyword" },
        |      "teid": { "type": "long" },
        |      "prid": { "type": "long" },
        |      "deid": { "type": "long" },
        |      "coid": { "type": "long" },
        |      "sna":  { "type": "keyword" },
        |      "ana":  { "type": "keyword" },
        |      "sid":  { "type": "keyword" },
        |      "nse":  { "type": "double" },
        |      "se":   { "type": "keyword" },
        |      "ame":  { "type": "keyword", "index": false },
        |      "dt":   { "type": "keyword" },
        |      "meta": { "type": "object", "enabled": false },
        |      "wid":  { "type": "long" },
        |      "wna":  { "type": "keyword" },
        |      "wti":  { "type": "keyword" }
        |    }
        |  }
        |}""".stripMargin
    run("indexExists")(client.execute(indexExists(index))).flatMap { exists =>
      if (exists.exists) Future.successful(())
      else run(s"createIndex ${index}")(client.execute(createIndex(index).source(mapping))).map(_ => ())
    }
  }

  def dropIndex(): Future[Unit] = {
    execute("dropIndex")(client.execute(deleteIndex(index))).map { resp =>
      if (resp.isError) 
        log.warn(s"dropIndex ${index}: ${Try(resp.error.reason).getOrElse(resp.toString)}")
      ()
    }
  }

  override def close(): Unit = Try(client.close()).getOrElse(())

  private def fromHit(id: String, src: Map[String, Any], json: String): Alert = {
    if (src != null && src.nonEmpty) Alert.fromHit(id, src)
    else Alert.fromSourceJson(id, json)
  }
}

object EventStoreElastic {
  def apply(elasticUri: String)(implicit ec: ExecutionContext): EventStoreElastic = {
    val uri = ElasticURI(elasticUri)
    new EventStoreElastic(ElasticClients.connect(uri), ElasticClients.resolveIndex(uri))
  }

  /** `detector-alert-search` + ts in 2026 UTC -> `detector-alert-2026`. */
  def yearlyIndex(searchIndex: String, ts: Long): String = {
    val prefix =
      if (searchIndex.endsWith("-search")) searchIndex.substring(0, searchIndex.length - "-search".length)
      else searchIndex
    val year = Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).getYear
    s"${prefix}-${year}"
  }
}
