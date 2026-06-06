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

/** JSON for arbitrary rule metadata: `Map[String, Any]` (nested objects/arrays supported). */
object ExplainMetaJson {
  private def anyToJs(x: Any): JsValue = x match {
    case null         => JsNull
    case v: JsValue   => v
    case b: Boolean   => JsBoolean(b)
    case n: Int       => JsNumber(n)
    case n: Long      => JsNumber(n)
    case n: Double    => JsNumber(n)
    case n: Float     => JsNumber(n.toDouble)
    case s: String    => JsString(s)
    case m: Map[_, _] => mapToJs(m.asInstanceOf[Map[String, Any]])
    case seq: Seq[_]  => JsArray(seq.asInstanceOf[Seq[Any]].map(anyToJs).toVector)
    case other        => JsString(other.toString)
  }

  private def jsToAny(json: JsValue): Any = json match {
    case JsNull           => null
    case JsTrue           => true
    case JsFalse          => false
    case JsNumber(n)      => if (n.isValidInt) n.toIntExact else if (n.isValidLong) n.toLongExact else n.toDouble
    case JsString(s)      => s
    case JsArray(elems)   => elems.map(jsToAny).toList
    case JsObject(fields) => fields.map { case (k, v) => k -> jsToAny(v) }.toMap
  }

  private def mapToJs(m: Map[String, Any]): JsObject =
    JsObject(m.map { case (k, v) => k -> anyToJs(v) })

  implicit val mapFormat: RootJsonFormat[Map[String, Any]] = new RootJsonFormat[Map[String, Any]] {
    def write(m: Map[String, Any]): JsValue = mapToJs(m)
    def read(json: JsValue): Map[String, Any] = json match {
      case JsObject(fields) => fields.map { case (k, v) => k -> jsToAny(v) }.toMap
      case JsNull           => Map.empty
      case other            => deserializationError(s"Expected meta object, got $other")
    }
  }

  implicit val optMapFormat: RootJsonFormat[Option[Map[String, Any]]] = new RootJsonFormat[Option[Map[String, Any]]] {
    def write(o: Option[Map[String, Any]]): JsValue = o match {
      case Some(m) => mapToJs(m)
      case None    => JsNull
    }
    def read(json: JsValue): Option[Map[String, Any]] = json match {
      case JsNull  => None
      case _       => Some(mapFormat.read(json))
    }
  }
}


object ExplainJson extends JsonCommon {

  /** Merge request `data` with indexed `schema` / `config` maps for ScriptFlow `input`. */
  def buildInput(req: ExplainReq): String = {
    val extra = scala.collection.mutable.LinkedHashMap[String, JsValue]()
    req.schema.filter(_.nonEmpty).foreach(s => extra += "schema" -> indexById(s, "schema_id"))
    req.config.filter(_.nonEmpty).foreach(c => extra += "config" -> indexById(c, "config_id"))
    JsObject(req.data.fields ++ extra.toMap).compactPrint
  }

  private def indexById(items: Seq[JsObject], idField: String): JsObject = {
    JsObject(
      items.flatMap { obj =>
        idFromField(obj, idField).map(id => id -> obj)
      }.toMap
    )
  }

  private def idFromField(obj: JsObject, idField: String): Option[String] =
    obj.fields.get(idField).flatMap {
      case JsString(s) if s.nonEmpty => Some(s)
      case JsNumber(n)               => Some(n.toString)
      case _                         => None
    }

  private def readJsObjectSeq(json: JsValue): Option[Seq[JsObject]] = json match {
    case JsArray(elems) =>
      val objs = elems.collect { case o: JsObject => o }
      if (objs.isEmpty) None else Some(objs)
    case o: JsObject => Some(Seq(o))
    case _           => None
  }

  implicit val jf_script_def: RootJsonFormat[ExplainScript] = ExplainScriptJson.jsonFormat
  implicit val jf_metaMap: JsonFormat[Map[String, Any]] = ExplainMetaJson.mapFormat  

  implicit val jf_explain: RootJsonFormat[Explain] = jsonFormat9(Explain.apply)
  implicit val jf_explains: RootJsonFormat[Explains] = jsonFormat2(Explains)
  implicit val jf_explain_search: RootJsonFormat[ExplainSearchReq] = jsonFormat3(ExplainSearchReq)
  implicit val jf_explain_create: RootJsonFormat[ExplainCreateReq] = jsonFormat7(ExplainCreateReq)
  implicit val jf_explain_update: RootJsonFormat[ExplainUpdateReq] = jsonFormat7(ExplainUpdateReq)
  implicit val jf_explain_action_res: RootJsonFormat[ExplaineActionRes] = jsonFormat2(ExplaineActionRes)

  implicit val jf_explain_req: RootJsonFormat[ExplainReq] = new RootJsonFormat[ExplainReq] {
    def write(r: ExplainReq): JsValue = {
      val m = scala.collection.mutable.LinkedHashMap[String, JsValue](
        "oid"  -> r.oid.toJson,
        "rid"  -> r.rid.toJson,
        "data" -> r.data,
      )
      r.fmt.foreach(f => m += "fmt" -> f.toJson)
      r.schema.foreach(s => m += "schema" -> JsArray(s.toVector))
      r.config.foreach(c => m += "config" -> JsArray(c.toVector))
      JsObject(m.toMap)
    }
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
        data = fields.get("data").filter(_ != JsNull).map(_.asJsObject).getOrElse(JsObject.empty),
        fmt = fields.get("fmt").filter(_ != JsNull).map(_.convertTo[String]),
        schema = fields.get("schema").filter(_ != JsNull).flatMap(readJsObjectSeq),
        config = fields.get("config").filter(_ != JsNull).flatMap(readJsObjectSeq),
      )
    }
  }

  implicit val jf_explain_res: RootJsonFormat[ExplainRes] = jsonFormat8(ExplainRes)
}
