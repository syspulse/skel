package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

// This file contains tests for Script object build methods.
// Other tests have been split into separate spec files:
// - ScriptRegexpSpec.scala
// - ScriptJQSpec.scala
// - ScriptJSSpec.scala
// - ScriptAISpec.scala
// - ScriptFlowSpec.scala
// - ScriptScoreSpec.scala
// - ScriptFlowConcurrencySpec.scala
//
// Run individual specs for faster test execution:
//   sbt "project skel_script" "testOnly io.syspulse.skel.script.ScriptRegexpSpec"
//   sbt "project skel_script" "testOnly io.syspulse.skel.script.ScriptJQSpec"
//   sbt "project skel_script" "testOnly io.syspulse.skel.script.ScriptJSSpec"
//   etc.

class ScriptSpec extends AnyWordSpec with Matchers {

  "Script object build methods" should {
    "build ScriptRegexp from builder" in {
      val script = ScriptRegexp.build(Some(".*foo.*"))
      script.getId() shouldBe "regexp"
      script.run("", "foobar", Map.empty) shouldBe Success("foobar")
    }

    "build ScriptJQ from builder" in {
      val script = ScriptJQ.build(Some(".name"))
      script.getId() shouldBe "jq"
    }

    "build ScriptJS from builder" in {
      val script = ScriptJS.build(None)
      script.getId() shouldBe "js"
    }

    "build ScriptJS from builder with src0" in {
      val script = ScriptJS.build(Some("input.toUpperCase()"))
      script.getId() shouldBe "js"
      // Test that src0 is used when run("") is called
      val result = script.run("", "hello", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "HELLO"
    }
    
    // Note: More comprehensive ScriptJS tests are in ScriptJSSpec.scala

    "build ScriptAI from builder" in {
      val script = ScriptAI.build(Some("openrouter://model"))
      script.getId() shouldBe "ai"
    }

    "build ScriptStr from builder" in {
      val script = ScriptStr.build(None)
      script.getId() shouldBe "str"
    }

    "build ScriptNone from builder" in {
      val script = ScriptNone.build(None)
      script.getId() shouldBe "none"
    }

    "build ScriptRegexpScore from builder" in {
      val script = ScriptRegexpScore.build(Some(".*foo.*"))
      script.getId() shouldBe "regexp_score"
      script.run("", "foobar", Map.empty) shouldBe Success("1.0")
      script.run("", "barbaz", Map.empty) shouldBe Success("0.0")
    }

    "build ScriptRegexpScore from builder with extraction pattern" in {
      val script = ScriptRegexpScore.build(Some("?([0-9]+)"))
      script.getId() shouldBe "regexp_score"
      script.run("", "The number is 42", Map.empty) shouldBe Success("1.0")
      script.run("", "No numbers here", Map.empty) shouldBe Success("0.0")
    }

    "build ScriptRegexpScore from builder with negation pattern" in {
      val script = ScriptRegexpScore.build(Some("!.*foo.*"))
      script.getId() shouldBe "regexp_score"
      script.run("", "barbaz", Map.empty) shouldBe Success("1.0")
      script.run("", "foobar", Map.empty) shouldBe Success("0.0")
    }

    "build ScriptJQScore from builder" in {
      val script = ScriptJQScore.build(Some(".name"))
      script.getId() shouldBe "jq_score"
      script.run("", """{"name":"John","age":30}""", Map.empty) shouldBe Success("1.0")
    }

    "build ScriptJQScore from builder (negative - field not found)" in {
      val script = ScriptJQScore.build(Some(".nonexistent"))
      script.getId() shouldBe "jq_score"
      val runResult = script.run("", """{"name":"John"}""", Map.empty)
      runResult.isFailure shouldBe true
      runResult.failed.get shouldBe a[java.util.NoSuchElementException]
    }

    "build ScriptSQScore from builder" in {
      val script = ScriptSQScore.build(Some("result"))
      script.getId() shouldBe "sq_score"
      script.name shouldBe "solidity-query-score"
    }

    "build ScriptSQScore from builder (empty pattern)" in {
      val script = ScriptSQScore.build(Some(""))
      script.getId() shouldBe "sq_score"
      script.name shouldBe "solidity-query-score"
    }
  }

  // Note: Comprehensive ScriptJS tests have been moved to ScriptJSSpec.scala
}
