package io.syspulse.skel.wf.ext.event

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class EventStoreMem extends EventStore {
  private val byId = new ConcurrentHashMap[String, Alert]()

  def upsert(alerts: Seq[Alert]): Future[Seq[Alert]] = {
    alerts.foreach(a => byId.put(a.id, a))
    Future.successful(alerts)
  }

  def getById(id: String): Future[Option[Alert]] =
    Future.successful(Option(byId.get(id)))

  def getByEid(eid: String, oid: Option[Long] = None): Future[Seq[Alert]] =
    Future.successful(all.filter(a => a.eid == eid && oid.forall(_ == a.teid)))

  def query(q: EventQuery): Future[EventPage] = {
    val matched = all
      .filter(a => q.ts0.forall(a.ts >= _))
      .filter(a => q.ts1.forall(a.ts <= _))
      .filter(a => q.oid.forall(_ == a.teid))
      .filter(a => q.pid.forall(_ == a.prid))
      .filter(a => q.did.forall(_ == a.deid))
      .filter(a => q.cid.forall(_ == a.coid))
      .filter(a => q.sid.forall(_ == a.sid))
      .sortBy(a => -a.ts)
    val total = matched.size.toLong
    val from = q.from.getOrElse(0L).toInt.max(0)
    val size = q.size.getOrElse(10L).toInt.max(0)
    val sliced = matched.slice(from, from + size)
    Future.successful(EventPage(sliced, total))
  }

  def delById(id: String): Future[Boolean] =
    Future.successful(byId.remove(id) != null)

  def delByEid(eid: String, oid: Option[Long] = None): Future[Int] = {
    val ids = all.filter(a => a.eid == eid && oid.forall(_ == a.teid)).map(_.id)
    ids.foreach(byId.remove)
    Future.successful(ids.size)
  }

  private def all: Seq[Alert] = byId.values.asScala.toSeq
}
