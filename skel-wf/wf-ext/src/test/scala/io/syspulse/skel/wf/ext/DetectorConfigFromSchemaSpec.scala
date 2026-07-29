package io.syspulse.skel.wf.ext

import scala.io.Source

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import spray.json._

import io.hacken.ext.detector.DetectorSchema
import io.syspulse.skel.wf.ext.store.WorkflowStore

/**
 * End-to-end: DetectorConfig.config instantiated from DetectorSchema.schema (a real JsonSchema
 * spec, loaded from test resources) via WorkflowStore.detectorConfigOf -> JsonSchemaDefault.of.
 */
class DetectorConfigFromSchemaSpec extends AnyWordSpec with Matchers {

  def loadSchema(resource: String): JsObject =
    Source.fromResource(resource).mkString.parseJson.asJsObject

  def detectorSchema(id: Int, name: String, schema: JsObject): DetectorSchema =
    DetectorSchema(
      id = id, createdAt = 0L, updatedAt = 0L, status = "ACTIVE",
      name = name, version = "1.0.0", title = name, description = "", author = "",
      icon = None, faq = None, tags = Seq(), networkTags = Seq(),
      schema = Some(schema), uiSchema = None,
    )

  "DetectorConfig created from a DetectorSchema.schema (JsonSchema)" should {

    "instantiate config from schema-Whales.json: literal array default AND scalar defaults" in {
      val schema = loadSchema("schema-Whales.json")
      val ds = detectorSchema(1, "Whales", schema)
      val dc = WorkflowStore.detectorConfigOf(10, ds)

      dc.config shouldBe defined
      val cfg = dc.config.get.fields

      // fields WITH an explicit scalar `default`
      cfg("to") shouldBe JsString("")
      cfg("from") shouldBe JsString("")
      cfg("desc") shouldBe JsString("{action} {dir} {amount} {sym}")
      cfg("severity") shouldBe JsNumber(-1)
      cfg("track_to") shouldBe JsBoolean(true)
      cfg("track_addr") shouldBe JsBoolean(true)
      cfg("track_from") shouldBe JsBoolean(true)

      // `tokens` is an array whose `default` is a literal list of Token objects (referenced via
      // `$ref` in `items`, but the `default` on the array itself wins and is copied verbatim)
      val tokens = cfg("tokens").asInstanceOf[JsArray].elements
      tokens should have size 5
      tokens.map(_.asJsObject.fields("symbol")) shouldBe Seq(
        JsString("USDT"), JsString("USDC"), JsString("WETH"), JsString("ETH"), JsString("BNB")
      )
      val usdt = tokens.head.asJsObject.fields
      usdt("address") shouldBe JsString("0xdAC17F958D2ee523a2206206994597C13D831ec7")
      usdt("decimals") shouldBe JsNumber(6)
      usdt("threshold") shouldBe JsString(">10000000")
    }

    "instantiate config from schema-Auditor1.json: defaults, nullable-type fallbacks and empty array (no default)" in {
      val schema = loadSchema("schema-Auditor1.json")
      val ds = detectorSchema(2, "Auditor1", schema)
      val dc = WorkflowStore.detectorConfigOf(11, ds)

      dc.config shouldBe defined
      val cfg = dc.config.get.fields
      cfg should have size 30 // one entry per top-level `properties` key

      // fields WITH a `default` (scalar, string, boolean, integer, const/oneOf-backed string)
      cfg("agentic_auditor_enabled") shouldBe JsBoolean(true)
      cfg("guardrail_local_enabled") shouldBe JsBoolean(true) // nullable type, but `default` wins
      cfg("agentic_script_style_model") shouldBe JsString("gpt-5.5")
      cfg("agentic_script_style_skill") shouldBe JsString("solidity-auditor") // default wins over oneOf/const
      cfg("agentic_script_style_pipeline_mode") shouldBe JsString("auditor")
      cfg("agentic_audit_hard_budget_usd") shouldBe JsNumber(350)
      cfg("agentic_audit_max_tokens_per_audit") shouldBe JsNumber(0)
      cfg("agentic_script_style_daml_max_chunks") shouldBe JsNumber(12)
      cfg("agentic_script_style_daml_poc_enabled") shouldBe JsBoolean(false)
      cfg("agentic_script_style_persona_fanout_passes") shouldBe JsNumber(2)
      cfg("agentic_script_style_final_synthesis_tool_trace_max_chars") shouldBe JsNumber(60000)

      // fields WITHOUT a `default` - fall back to the (first non-null) declared `type`
      cfg("billing_client_id") shouldBe JsString("")                // type: ["string", "null"]
      cfg("billing_project_id") shouldBe JsString("")                // type: ["string", "null"]
      cfg("llm_credential_profile") shouldBe JsString("")            // type: ["string", "null"]
      cfg("include_documentation") shouldBe JsBoolean(false)         // type: ["boolean", "null"], no default
      cfg("guardrail_testsavant_enabled") shouldBe JsBoolean(false)  // type: ["boolean", "null"], no default
      cfg("agentic_script_style_step_model_overrides") shouldBe JsArray() // type: ["array", "null"], no minItems
    }
  }
}
