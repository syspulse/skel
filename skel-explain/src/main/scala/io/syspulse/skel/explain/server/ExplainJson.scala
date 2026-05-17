package io.syspulse.skel.explain.server

import io.syspulse.skel.service.JsonCommon
import spray.json._
import io.syspulse.skel.explain.{ExplainRule, ScriptDef}

object ExplainJson extends JsonCommon {

  // ScriptDef format defined in companion object — brought in via import
  implicit val jf_script_def = ScriptDef.jsonFormat

  implicit val jf_explain_rule = jsonFormat5(ExplainRule.apply)
  implicit val jf_explain_rules = jsonFormat2(ExplainRules)
  implicit val jf_explain_rule_create = jsonFormat2(ExplainRuleCreateReq)
  implicit val jf_explain_rule_update = jsonFormat2(ExplainRuleUpdateReq)
  implicit val jf_explain_rule_res = jsonFormat2(ExplainRuleRes)

  implicit val jf_explain_req: RootJsonFormat[ExplainReq] = new RootJsonFormat[ExplainReq] {
    def write(r: ExplainReq): JsValue = JsObject(
      "oid"    -> r.oid.toJson,
      "rid"    -> r.rid.toJson,
      "schema" -> r.schema.toJson,
      "data"   -> r.data.toJson
    )
    def read(json: JsValue): ExplainReq = {
      val fields = json.asJsObject.fields
      ExplainReq(
        oid = fields.get("oid").filter(_ != JsNull).map(v => v match {
          case JsString(s) => s
          case JsNumber(n) => n.toString
          case other       => other.convertTo[String]
        }),
        rid    = fields.get("rid").filter(_ != JsNull).map(_.convertTo[String]),
        schema = fields.get("schema").filter(_ != JsNull),
        data   = fields.get("data").filter(_ != JsNull).map(_.asJsObject).getOrElse(JsObject.empty)
      )
    }
  }

  implicit val jf_explain_res = jsonFormat4(ExplainRes.apply)
}
