package io.syspulse.skel.explain

import spray.json._
import DefaultJsonProtocol._

case class ScriptDef(
  typ: String,            // engine type: "js", "ai", "jq", "regexp", "str", etc.
  src: String,            // script source / prompt
  opts: Option[String] = None  // optional model URI for AI, etc.
)

object ScriptDef {
  implicit val jsonFormat: RootJsonFormat[ScriptDef] = new RootJsonFormat[ScriptDef] {
    def write(s: ScriptDef): JsValue = {
      val m = scala.collection.mutable.LinkedHashMap[String, JsValue](
        "typ" -> s.typ.toJson,
        "src" -> s.src.toJson
      )
      s.opts.foreach(o => m += "opts" -> o.toJson)
      JsObject(m.toMap)
    }
    def read(json: JsValue): ScriptDef = {
      val f = json.asJsObject.fields
      ScriptDef(
        typ  = f("typ").convertTo[String],
        src  = f("src").convertTo[String],
        opts = f.get("opts").filter(_ != JsNull).map(_.convertTo[String])
      )
    }
  }
}

case class ExplainRule(
  oid: String,              // owner id, "" = default
  rid: String,              // rule id (mandatory)
  scripts: Seq[ScriptDef],  // ordered list of script definitions
  name: Option[String] = None,
  ts: Long = System.currentTimeMillis()
)

object ExplainRule {
  val DEF_OID = ""
}
