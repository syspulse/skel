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
// - ScriptConditionSpec.scala
// - ScriptFilterSpec.scala
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

    "build ScriptFilter from builder" in {
      val script = ScriptFilter.build(None)
      script.getId() shouldBe "filter"
      script.name shouldBe "filter"
    }

    "build ScriptFilter from builder with src" in {
      val script = ScriptFilter.build(Some("CUSTOM_SRC"))
      script.getId() shouldBe "filter"
      // ScriptFilter.build now returns a new instance with src0
      script shouldBe a[ScriptFilter]
    }

    "ScriptFilter uses input as ScriptBreakException message when short-circuiting" in {
      val script = ScriptFilter.build(None)
      
      // Non-empty input - should pass through
      val result1 = script.run("", "test", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "test"
      
      // Empty input - Filter uses input (not run src) as exception message
      val result2 = script.run("NO_DATA", "", Map.empty)
      result2.isFailure shouldBe true
      result2.failed.get shouldBe a[Script.ScriptBreakException]
      result2.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""
    }

    "ScriptFilter propagates empty src when short-circuiting with empty src" in {
      val script = ScriptFilter.build(None)
      
      // Empty input with empty src - should propagate empty string
      val result = script.run("", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe ""
    }

    "ScriptFilter passes non-empty input through regardless of src" in {
      val script = ScriptFilter.build(None)
      
      // Non-empty input should pass through even with custom src
      val customSrc = "CUSTOM_SRC"
      val result = script.run(customSrc, "test", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "test" // Input is passed through, src is ignored when input is non-empty
    }

    "build ScriptCondition from builder" in {
      val script = ScriptCondition.build(Some("> 0"))
      script.getId() shouldBe "condition"
      script.name shouldBe "condition"
    }

    "ScriptCondition passes when condition is satisfied" in {
      val script = ScriptCondition.build(Some("> 10"))
      script.run("", "15", Map.empty) shouldBe Success("15")
    }

    "ScriptCondition fails with ScriptBreakException when condition not satisfied" in {
      val script = ScriptCondition.build(Some("> 10"))
      val result = script.run("", "5", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe "> 10"
    }
  }

  // Note: Comprehensive ScriptJS tests have been moved to ScriptJSSpec.scala
  // Note: Comprehensive ScriptCondition tests are in ScriptConditionSpec.scala
}
