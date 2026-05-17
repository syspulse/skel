package io.syspulse.skel.explain.server

import spray.json.JsValue
import spray.json.JsObject
import io.syspulse.skel.explain.{ExplainRule, ScriptDef}

// Input request for the explain endpoint
final case class ExplainReq(
  oid: Option[String] = None,
  rid: Option[String] = None,
  schema: Option[JsValue] = None,
  data: JsObject = JsObject.empty
)

// Output response from explain
final case class ExplainRes(
  explanation: String,
  ts: Long = System.currentTimeMillis(),
  scripts: Seq[String] = Seq.empty,   // engine type names, e.g. ["js", "ai"]
  oid: Option[String] = None
)

// Collection of rules
final case class ExplainRules(
  data: Seq[ExplainRule],
  total: Option[Long] = None
)

// Request to create a rule
final case class ExplainRuleCreateReq(
  scripts: Seq[ScriptDef],
  name: Option[String] = None
)

// Request to update a rule (partial — only provided fields are changed)
final case class ExplainRuleUpdateReq(
  scripts: Option[Seq[ScriptDef]] = None,
  name: Option[String] = None
)

// Response for rule CRUD operations
final case class ExplainRuleRes(
  oid: String,
  rid: String
)
