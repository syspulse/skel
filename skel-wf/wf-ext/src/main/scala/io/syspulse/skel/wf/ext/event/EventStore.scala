package io.syspulse.skel.wf.ext.event

import scala.concurrent.Future

trait EventStore {
  def upsert(alerts: Seq[Alert]): Future[Seq[Alert]]
  def getById(id: String): Future[Option[Alert]]
  def getByEid(eid: String, oid: Option[Long] = None): Future[Seq[Alert]]
  def query(q: EventQuery): Future[EventPage]
  def delById(id: String): Future[Boolean]
  def delByEid(eid: String, oid: Option[Long] = None): Future[Int]
  def close(): Unit = ()
}

object EventStore {
  val DEF_INDEX = "detector-alert"
}
