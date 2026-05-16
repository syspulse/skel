package io.syspulse.skel.explain.server

import spray.json.JsValue
import spray.json.JsObject
import io.syspulse.skel.explain.ExplainRule

// Input request for the explain endpoint
final case class ExplainReq(
  oid: Option[String] = None,   // Optional owner id, uses default rule if absent
  rid: Option[String] = None,   // Optional rule id (can come from URL path)
  schema: Option[JsValue] = None,
  data: JsObject = JsObject.empty
)

// Output response from explain
final case class ExplainRes(
  explanation: String,
  ts: Long = System.currentTimeMillis(),
  scripts: Seq[String] = Seq.empty,
  oid: Option[String] = None
)

// Collection of rules
final case class ExplainRules(
  data: Seq[ExplainRule],
  total: Option[Long] = None
)

// Request to create a rule
final case class ExplainRuleCreateReq(
  scripts: Seq[String],
  name: Option[String] = None
)

// Request to update a rule
final case class ExplainRuleUpdateReq(
  scripts: Option[Seq[String]] = None,
  name: Option[String] = None
)

// Response for rule CRUD operations
final case class ExplainRuleRes(
  oid: String,
  rid: String
)
