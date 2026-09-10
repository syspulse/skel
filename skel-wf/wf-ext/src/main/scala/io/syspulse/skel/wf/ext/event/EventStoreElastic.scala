package io.syspulse.skel.wf.ext.event

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import com.typesafe.scalalogging.Logger
import com.sksamuel.elastic4s.{ElasticClient, RequestFailure, RequestSuccess, Response}
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.queries.Query

import io.syspulse.skel.uri.ElasticURI

/**
 * Async OpenSearch store (elastic4s). All operations return `Future` and never block on `.await`.
 *
 * Writes use `_id = {deid}:{eid}` so a second create with the same pair overwrites (including `ts`).
 */
class EventStoreElastic(val client: ElasticClient, index0: String)(implicit ec: ExecutionContext) extends EventStore {
  private val log = Logger(this.getClass)
  val index: String = Option(index0).map(_.trim).filter(_.nonEmpty).getOrElse(EventStore.DEF_INDEX)

  import ElasticDsl._

  private def unwrap[T](resp: Response[T], op: String): T = resp match {
    case e: RequestFailure =>
      val msg = Try(e.error.reason).toOption.filter(_ != null).getOrElse(e.body.getOrElse(e.toString))
      throw new RuntimeException(s"OpenSearch ${op} failed: ${msg}")
    case s: RequestSuccess[T] => s.result
  }

  def upsert(alerts: Seq[Alert]): Future[Seq[Alert]] = {
    if (alerts.isEmpty) return Future.successful(Seq.empty)
    val reqs = alerts.map { a =>
      indexInto(index)
        .id(a.id)
        .source(Alert.sourceJson(a).compactPrint)
        .refresh(RefreshPolicy.WaitFor)
    }
    client.execute(bulk(reqs).refresh(RefreshPolicy.WaitFor)).map { resp =>
      unwrap(resp, "bulk upsert")
      alerts
    }
  }

  def getById(id: String): Future[Option[Alert]] = {
    client.execute(ElasticDsl.get(index, id)).flatMap { resp =>
      if (resp.isError) {
        val reason = Try(resp.error.reason).getOrElse("")
        if (reason.contains("index_not_found") || reason.contains("no such index")) Future.successful(None)
        else if (reason.contains("more than one index") || reason.toLowerCase.contains("alias")) getByIdSearch(id)
        else Future.failed(new RuntimeException(s"OpenSearch get failed: ${reason}"))
      } else {
        val r = resp.result
        if (!r.found) Future.successful(None)
        else Future.successful(Some(fromHit(r.id, r.sourceAsMap, r.sourceAsString)))
      }
    }
  }

  private def getByIdSearch(id: String): Future[Option[Alert]] = {
    client.execute(ElasticDsl.search(index).query(idsQuery(id)).size(1)).map { resp =>
      val r = unwrap(resp, "getById")
      r.hits.hits.headOption.map(h => fromHit(h.id, h.sourceAsMap, h.sourceAsString))
    }
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

    client.execute(sreq).map { resp =>
      val r = unwrap(resp, "search")
      val alerts = r.hits.hits.toSeq.map { h =>
        fromHit(h.id, h.sourceAsMap, h.sourceAsString)
      }
      EventPage(alerts, r.totalHits)
    }
  }

  def delById(id: String): Future[Boolean] = {
    client.execute(deleteById(index, id).refresh(RefreshPolicy.WaitFor)).map { resp =>
      if (resp.isError) {
        val reason = Try(resp.error.reason).getOrElse("")
        if (reason.contains("index_not_found") || reason.contains("404")) false
        else throw new RuntimeException(s"OpenSearch delete failed: ${reason}")
      } else resp.result.result == "deleted"
    }
  }

  def delByEid(eid: String, oid: Option[Long] = None): Future[Int] = {
    val filters = scala.collection.mutable.ListBuffer[Query](termQuery("eid", eid))
    oid.foreach(v => filters += termQuery("teid", v))
    client.execute(
      deleteByQuery(index, boolQuery().filter(filters.toList)).refreshImmediately.waitForCompletion(true)
    ).map { resp =>
      unwrap(resp, "deleteByQuery") match {
        case Left(r)  => r.deleted.toInt
        case Right(_) => 0
      }
    }
  }

  /** Create the index with detector-alert-compatible mappings. Idempotent. Not used on Dev. */
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
        |      "sna":  { "type": "keyword" },
        |      "ana":  { "type": "keyword" },
        |      "sid":  { "type": "keyword" },
        |      "nse":  { "type": "double" },
        |      "ame":  { "type": "keyword" },
        |      "meta": { "type": "object", "enabled": false },
        |      "wid":  { "type": "keyword" }
        |    }
        |  }
        |}""".stripMargin
    client.execute(indexExists(index)).flatMap { existsResp =>
      val exists = existsResp match {
        case s: RequestSuccess[_] => s.result.exists
        case _ => false
      }
      if (exists) Future.successful(())
      else client.execute(createIndex(index).source(mapping)).map { r =>
        unwrap(r, s"createIndex ${index}")
        ()
      }
    }
  }

  def dropIndex(): Future[Unit] = {
    client.execute(deleteIndex(index)).map { resp =>
      if (resp.isError) log.warn(s"dropIndex ${index}: ${Try(resp.error.reason).getOrElse(resp.toString)}")
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
}
