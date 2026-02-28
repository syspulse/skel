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
      val ex = result.failed.get.asInstanceOf[Script.ScriptBreakException]
      ex.src shouldBe ""
      ex.getMessage shouldBe "Break: ''"
    }

    "return Failure(ScriptBreakException) for blank (whitespace) input" in {
      val script = ScriptFilter.build(None)
      val result = script.run("", "   ", Map.empty)
      result.isFailure shouldBe true
      val ex = result.failed.get.asInstanceOf[Script.ScriptBreakException]
      ex.src shouldBe "   "  // Filter uses input as exception message
      ex.getMessage shouldBe "Break: '   '"
    }

    "ScriptBreakException carries input (not run src) when empty input" in {
      val script = ScriptFilter.build(None)
      val result = script.run("NO_DATA", "", Map.empty)
      result.isFailure shouldBe true
      val ex = result.failed.get.asInstanceOf[Script.ScriptBreakException]
      ex.src shouldBe ""  // Filter uses input as message
      ex.getMessage shouldBe "Break: ''"
    }

    "propagate empty input as exception src when empty run() src" in {
      val script = ScriptFilter.build(None)
      val result = script.run("", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""
    }

    "ScriptBreakException carries input when empty and build(Some(uriSrc))" in {
      val script = ScriptFilter.build(Some("URI_NO_DATA"))
      val result = script.run("", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""  // input, not URI
    }

    "ScriptBreakException carries input when empty and both URI and run src set" in {
      val script = ScriptFilter.build(Some("FROM_URI"))
      val result = script.run("FROM_RUN", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""  // input
    }

    "ScriptBreakException carries input when empty and build(Some(\"\"))" in {
      val script = ScriptFilter.build(Some(""))
      val result = script.run("FALLBACK_SRC", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""  // input
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
