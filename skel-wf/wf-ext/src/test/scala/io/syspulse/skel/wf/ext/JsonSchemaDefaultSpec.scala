package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import spray.json._
import io.hacken.ext.detector.JsonSchemaDefault

class JsonSchemaDefaultSpec extends AnyWordSpec with Matchers {

  "JsonSchemaDefault.of" should {

    "instantiate primitive defaults for a flat object schema" in {
      val schema = """{
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "count": { "type": "integer" },
          "ratio": { "type": "number" },
          "enabled": { "type": "boolean" },
          "tags": { "type": "array" }
        }
      }""".parseJson.asJsObject

      JsonSchemaDefault.of(schema) shouldBe JsObject(
        "name" -> JsString(""),
        "count" -> JsNumber(0),
        "ratio" -> JsNumber(0),
        "enabled" -> JsBoolean(false),
        "tags" -> JsArray(),
      )
    }

    "prefer an explicit `default` over the type-based default" in {
      val schema = """{
        "type": "object",
        "properties": {
          "severity": { "type": "number", "default": 0.5 }
        }
      }""".parseJson.asJsObject

      JsonSchemaDefault.of(schema) shouldBe JsObject("severity" -> JsNumber(0.5))
    }

    "use the first `enum` value when no `default` is set" in {
      val schema = """{
        "type": "object",
        "properties": {
          "mode": { "type": "string", "enum": ["A", "B"] }
        }
      }""".parseJson.asJsObject

      JsonSchemaDefault.of(schema) shouldBe JsObject("mode" -> JsString("A"))
    }

    "recurse into nested object properties" in {
      val schema = """{
        "type": "object",
        "properties": {
          "threshold": {
            "type": "object",
            "properties": {
              "min": { "type": "integer", "default": 1 },
              "max": { "type": "integer" }
            }
          }
        }
      }""".parseJson.asJsObject

      JsonSchemaDefault.of(schema) shouldBe JsObject(
        "threshold" -> JsObject("min" -> JsNumber(1), "max" -> JsNumber(0))
      )
    }

    "resolve a local $ref against root definitions" in {
      val schema = """{
        "type": "object",
        "properties": {
          "addr": { "$ref": "#/definitions/Address" }
        },
        "definitions": {
          "Address": { "type": "string", "default": "0x0" }
        }
      }""".parseJson.asJsObject

      JsonSchemaDefault.of(schema) shouldBe JsObject("addr" -> JsString("0x0"))
    }
  }
}
