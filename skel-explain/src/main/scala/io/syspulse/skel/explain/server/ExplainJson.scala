package io.syspulse.skel.explain.server

import io.syspulse.skel.service.JsonCommon
import spray.json._
import DefaultJsonProtocol._
import io.syspulse.skel.explain.{Explain, ExplainScript}

object ExplainScriptJson {
  implicit val jsonFormat: RootJsonFormat[ExplainScript] = new RootJsonFormat[ExplainScript] {
    def write(s: ExplainScript): JsValue = {
      val m = scala.collection.mutable.LinkedHashMap[String, JsValue](
        "typ" -> s.typ.toJson,
        "src" -> s.src.toJson
      )
      s.opts.foreach(o => m += "opts" -> o.toJson)
      JsObject(m.toMap)
    }
    def read(json: JsValue): ExplainScript = {
      val f = json.asJsObject.fields
      new ExplainScript(
        typ  = f("typ").convertTo[String],
        src  = f("src").convertTo[String],
        opts = f.get("opts").filter(_ != JsNull).map(_.convertTo[String])
      )
    }
  }
}


object ExplainJson extends JsonCommon {

  implicit val jf_script_def: RootJsonFormat[ExplainScript] = ExplainScriptJson.jsonFormat

  implicit val jf_explain: RootJsonFormat[Explain] = jsonFormat8(Explain.apply)
  implicit val jf_explains: RootJsonFormat[Explains] = jsonFormat2(Explains)
  implicit val jf_explain_create: RootJsonFormat[ExplainCreateReq] = jsonFormat6(ExplainCreateReq)
  implicit val jf_explain_update: RootJsonFormat[ExplainUpdateReq] = jsonFormat6(ExplainUpdateReq)
  implicit val jf_explain_action_res: RootJsonFormat[ExplaineActionRes] = jsonFormat2(ExplaineActionRes)

  implicit val jf_explain_req: RootJsonFormat[ExplainReq] = new RootJsonFormat[ExplainReq] {
    def write(r: ExplainReq): JsValue = JsObject(
      "oid"  -> r.oid.toJson,
      "rid"  -> r.rid.toJson,
      "data" -> r.data.toJson
    )
    def read(json: JsValue): ExplainReq = {
      val fields = json.asJsObject.fields
      ExplainReq(
        oid = fields.get("oid").filter(_ != JsNull).map(v => v match {
          case JsString(s) => s
          case JsNumber(n) => n.toString
          case other       => other.convertTo[String]
        }),
        rid = fields.get("rid").filter(_ != JsNull).map(v => v match {
          case JsString(s) => s
          case JsNumber(n) => n.toString
          case other       => other.convertTo[String]
        }),
        data = fields.get("data").filter(_ != JsNull).map(_.asJsObject).getOrElse(JsObject.empty)
      )
    }
  }

  implicit val jf_explain_res: RootJsonFormat[ExplainRes] = jsonFormat7(ExplainRes)
}
