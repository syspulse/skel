package io.syspulse.skel.wf.ext.event

import spray.json.JsObject

/** Event API body. Maps to Alert fields (see REQUIREMENTS-Workflow-Alerts.md). */
final case class EventCreateReq(
  ts: Long,
  eid: String,
  rid: Option[String] = None,
  oid: Long,
  pid: Long,
  cid: Long,
  did: Long,
  nid: String,
  name: Option[String] = None,
  wid: Long,
  wna: Option[String] = None,
  wti: Option[String] = None,
  sid: Option[String] = None,
  sev: Double,
  desc: Option[String] = None,
  meta: Option[JsObject] = None,
  tags: Option[Seq[String]] = None,
)

final case class Alerts(events: Seq[Alert], total: Long)

final case class EventActionRes(status: String, id: Option[String] = None)

object EventActionRes {
  val OK = "200"
  val NOT_FOUND = "404"
}

final case class EventQuery(
  ts0: Option[Long] = None,
  ts1: Option[Long] = None,
  oid: Option[Long] = None,
  pid: Option[Long] = None,
  did: Option[Long] = None,
  cid: Option[Long] = None,
  sid: Option[String] = None,
  from: Option[Long] = None,
  size: Option[Long] = None,
)

final case class EventPage(alerts: Seq[Alert], total: Long)
