package io.syspulse.skel.explain

case class ExplainRule(
  oid: String,            // owner id, "" = default
  rid: String,            // rule id (mandatory)
  scripts: Seq[String],   // list of script URIs (ScriptFlow format)
  name: Option[String] = None,
  ts: Long = System.currentTimeMillis()
)

object ExplainRule {
  val DEF_OID = ""
}
