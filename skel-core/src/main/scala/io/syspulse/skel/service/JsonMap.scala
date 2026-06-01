package io.syspulse.skel.service

import spray.json._
import DefaultJsonProtocol._

/** JSON for arbitrary user metadata: `Map[String, Any]` (nested objects/arrays supported). */
object JsonMap extends JsonMap

trait JsonMap {
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
