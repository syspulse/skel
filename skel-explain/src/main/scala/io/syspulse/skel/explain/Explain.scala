package io.syspulse.skel.explain

case class ExplainScript(
  typ: String,            // engine type: "js", "ai", "jq", "regexp", "str", etc.
  src: String,            // script source / prompt
  opts: Option[String] = None  // optional model URI for AI, etc.
)

case class Explain(
  oid: Option[String] = Explain.DEF_OID,         // owner id, "" = default
  rid: String,              // rule id (mandatory)
  scripts: Seq[ExplainScript],  // ordered list of script definitions
  name: Option[String] = None,
  desc: Option[String] = None,
  sid: Option[String] = None,   // optional schema id; multiple rules may reference the same schema
  
  meta: Option[Map[String, Any]] = None, // arbitrary metadata (e.g. icon, color, etc.)
  ts0: Long = System.currentTimeMillis(),
  ts: Long = System.currentTimeMillis()
)

object Explain {
  val DEF_OID = None
}
