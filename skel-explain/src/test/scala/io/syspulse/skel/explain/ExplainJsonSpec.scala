package io.syspulse.skel.explain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

import io.syspulse.skel.explain.server.{ExplainJson, ExplainReq}

class ExplainJsonSpec extends AnyWordSpec with Matchers {

  "ExplainJson.buildInput" should {
    "return empty object when data/schema/config are absent" in {
      ExplainJson.buildInput(ExplainReq()) shouldBe "{}"
    }

    "merge data with schema and config indexed by id" in {
      val req = ExplainReq(
        data = JsObject("address" -> JsString("0xABC")),
        schema = Some(Seq(
          JsObject("schema_id" -> JsString("wallet-v1"), "fields" -> JsArray(JsString("balance"))),
        )),
        config = Some(Seq(
          JsObject("config_id" -> JsString("default"), "threshold" -> JsNumber(1000)),
        )),
      )
      val parsed = ExplainJson.buildInput(req).parseJson.asJsObject
      parsed.fields("address") shouldBe JsString("0xABC")
      parsed.fields("schema").asJsObject.fields("wallet-v1").asJsObject.fields("fields") shouldBe
        JsArray(JsString("balance"))
      parsed.fields("config").asJsObject.fields("default").asJsObject.fields("threshold") shouldBe JsNumber(1000)
    }

    "omit schema and config keys when not provided" in {
      val parsed = ExplainJson.buildInput(ExplainReq(data = JsObject("x" -> JsNumber(1)))).parseJson.asJsObject
      parsed.fields.keys.toSet shouldBe Set("x")
    }
  }
}
