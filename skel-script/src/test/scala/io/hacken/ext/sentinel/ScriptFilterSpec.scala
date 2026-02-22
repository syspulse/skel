package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

class ScriptFilterSpec extends AnyWordSpec with Matchers {

  "ScriptFilter" should {
    "build from builder" in {
      val script = ScriptFilter.build(None)
      script.getId() shouldBe "filter"
      script.name shouldBe "filter"
    }

    "build with Some(src) for URI src0" in {
      val script = ScriptFilter.build(Some("NO_DATA"))
      script.getId() shouldBe "filter"
      script shouldBe a[ScriptFilter]
    }

    "pass through non-empty input" in {
      val script = ScriptFilter.build(None)
      script.run("", "test", Map.empty) shouldBe Success("test")
      script.run("", "x", Map.empty) shouldBe Success("x")
      script.run("", """{"a":1}""", Map.empty) shouldBe Success("""{"a":1}""")
    }

    "return Failure(ScriptBreakException) for empty input" in {
      val script = ScriptFilter.build(None)
      val result = script.run("", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""
    }

    "return Failure(ScriptBreakException) for blank (whitespace) input" in {
      val script = ScriptFilter.build(None)
      val result = script.run("", "   ", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
    }

    "propagate run() src when empty input and build(None)" in {
      val script = ScriptFilter.build(None)
      val customSrc = "NO_DATA"
      val result = script.run(customSrc, "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe customSrc
    }

    "propagate empty src when empty input and empty run() src" in {
      val script = ScriptFilter.build(None)
      val result = script.run("", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""
    }

    "use src0 from URI when empty input and build(Some(uriSrc))" in {
      val script = ScriptFilter.build(Some("URI_NO_DATA"))
      val result = script.run("", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe "URI_NO_DATA"
    }

    "prefer src0 from URI over run() src when both set and input empty" in {
      val script = ScriptFilter.build(Some("FROM_URI"))
      val result = script.run("FROM_RUN", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe "FROM_URI"
    }

    "use run() src when build(Some(\"\")) and input empty" in {
      val script = ScriptFilter.build(Some(""))
      val result = script.run("FALLBACK_SRC", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe "FALLBACK_SRC"
    }

    "pass through non-empty input regardless of src parameter" in {
      val script = ScriptFilter.build(None)
      val result = script.run("IGNORED_SRC", "test", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "test"
    }

    "pass through non-empty input when built with src0" in {
      val script = ScriptFilter.build(Some("NO_DATA"))
      script.run("", "payload", Map.empty) shouldBe Success("payload")
    }
  }
}
