package io.hacken.ext.detector

import spray.json._

/**
 * Instantiates a default `JsObject` (a config instance) from a JSON Schema `JsObject`
 * (`DetectorSchema.schema`), which is stored untyped (not a Scala type). This is the reverse
 * of what `andyglow/scala-jsonschema` does (Scala type -> JSON Schema, at compile time via
 * macros) - since here the schema itself is runtime DATA, there's no Scala type to reflect on,
 * so that library doesn't apply. Instead, this walks the schema (draft-04/06/07 style keywords)
 * and builds a default value for every node:
 *
 *   - `default` / `const` / `enum` (first value) win if present
 *   - `type: object`  -> JsObject of defaults for every `properties` entry
 *   - `type: array`   -> empty JsArray (or repeated item defaults if `minItems` set)
 *   - `type: string`  -> ""
 *   - `type: integer`/`number` -> 0
 *   - `type: boolean` -> false
 *   - `type: null`    -> JsNull
 *   - `$ref: "#/..."`  -> resolved against the root schema (e.g. `#/definitions/...`, `#/$defs/...`)
 */
object JsonSchemaDefault {

  /** Build a default JsObject instance from a JSON Schema `JsObject`. */
  def of(schema: JsObject): JsObject = build(schema, schema) match {
    case o: JsObject => o
    case other        => JsObject("value" -> other)
  }

  private def build(node: JsValue, root: JsObject): JsValue = node match {
    case o: JsObject => buildNode(o, root)
    case other        => other
  }

  private def buildNode(node: JsObject, root: JsObject): JsValue = {
    val fields = node.fields

    fields.get("default").orElse(fields.get("const")).orElse {
      fields.get("enum") match {
        case Some(JsArray(values)) if values.nonEmpty => Some(values.head)
        case _                                        => None
      }
    } match {
      case Some(v) => v
      case None    =>
        fields.get("$ref") match {
          case Some(JsString(ref)) => resolveRef(ref, root).map(build(_, root)).getOrElse(JsNull)
          case _                    => buildByType(fields, root)
        }
    }
  }

  private def buildByType(fields: Map[String, JsValue], root: JsObject): JsValue =
    typeOf(fields) match {
      case Some("object")                                  => buildObject(fields, root)
      case Some("array")                                    => buildArray(fields, root)
      case Some("string")                                    => JsString("")
      case Some("integer") | Some("number")                   => JsNumber(0)
      case Some("boolean")                                    => JsBoolean(false)
      case Some("null")                                       => JsNull
      case None if fields.contains("properties")             => buildObject(fields, root)
      case None if fields.contains("items")                   => buildArray(fields, root)
      case _                                                  => JsNull
    }

  private def typeOf(fields: Map[String, JsValue]): Option[String] = fields.get("type") match {
    case Some(JsString(t))    => Some(t)
    case Some(JsArray(types)) => types.collectFirst { case JsString(t) if t != "null" => t }
    case _                     => None
  }

  private def buildObject(fields: Map[String, JsValue], root: JsObject): JsObject =
    fields.get("properties") match {
      case Some(JsObject(props)) => JsObject(props.map { case (k, v) => k -> build(v, root) })
      case _                      => JsObject()
    }

  private def buildArray(fields: Map[String, JsValue], root: JsObject): JsValue = {
    val minItems = fields.get("minItems") match {
      case Some(JsNumber(n)) => n.toInt
      case _                  => 0
    }
    if (minItems <= 0) JsArray()
    else fields.get("items") match {
      case Some(item: JsObject) => JsArray(Vector.fill(minItems)(build(item, root)))
      case _                     => JsArray()
    }
  }

  /** Resolve a local `$ref` (e.g. `#/definitions/Foo`, `#/$defs/Foo`) against the root schema. */
  private def resolveRef(ref: String, root: JsObject): Option[JsValue] = {
    if (!ref.startsWith("#/")) None
    else {
      val path = ref.stripPrefix("#/").split("/").toList.filter(_.nonEmpty)
      path.foldLeft[Option[JsValue]](Some(root)) {
        case (Some(JsObject(fs)), key) => fs.get(key)
        case _                          => None
      }
    }
  }
}
