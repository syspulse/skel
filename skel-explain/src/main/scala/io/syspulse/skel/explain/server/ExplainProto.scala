package io.syspulse.skel.explain.server

import spray.json.JsObject
import io.syspulse.skel.explain.{Explain, ExplainScript}

// Input for the explain endpoint (body is optional on GET /{rid}/explain)
final case class ExplainReq(
  oid: Option[String] = None,
  rid: Option[String] = None,
  data: JsObject = JsObject.empty,

  fmt: Option[String] = None, // format of data, default is derived by Explain

  schema: Option[Seq[JsObject]] = None, // optional schemas for data
  config: Option[Seq[JsObject]] = None, // optional configs for data
  style: Option[String] = None, // optional style for explanation
)

// Output from explain
final case class ExplainRes(
  explanation: String,
  ts: Long = System.currentTimeMillis(),
  scripts: Seq[String] = Seq.empty,
  fmt: Option[String] = Some("markdown"),
  style: Option[String] = None,
  
  rid: String,
  oid: Option[String] = None,

  meta: Option[Map[String, Any]] = None,
)

// Collection of rules
final case class Explains(
  data: Seq[Explain],
  total: Option[Long] = None
)

final case class ExplainSearchReq(
  query: String,
  from: Option[Long] = None,
  size: Option[Long] = None,
)

// Request to create a rule
final case class ExplainCreateReq(
  oid: Option[String] = None,
  rid: Option[String] = None,
  scripts: Seq[ExplainScript],
  name: Option[String] = None,
  desc: Option[String] = None,
  sid: Option[String] = None,
  meta: Option[Map[String, Any]] = None
)

// Request to update a rule (only provided fields are changed)
final case class ExplainUpdateReq(
  oid: Option[String] = None,
  rid: Option[String] = None,
  scripts: Option[Seq[ExplainScript]] = None,
  name: Option[String] = None,
  desc: Option[String] = None,
  sid: Option[String] = None,
  meta: Option[Map[String, Any]] = None
)

// Response for rule CRUD operations
final case class ExplaineActionRes(
  oid: Option[String],
  rid: String,  
)
